// Spring Security 설정 (JWT 필터, OAuth2, CORS)
package com.codeprint.infrastructure.config;

import com.codeprint.infrastructure.security.ApiKeyAuthenticationFilter;
import com.codeprint.infrastructure.security.JwtAuthenticationFilter;
import com.codeprint.infrastructure.security.OAuth2SuccessHandler;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;

    // JWT 필터, OAuth2, CORS, 세션리스 정책을 적용한 Security 필터 체인 구성
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/users/me", "/api/users/me/**").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/users/*/follow").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/users/*/follow").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/community/posts/*/like").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/community/posts/*/like").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/community/posts").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/community/posts/*").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/graphs/*/nodes/*/comments").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/cron/**").permitAll()
                .requestMatchers("/api/auth/**", "/api/share/**", "/api/community/posts/*/graph", "/api/community/posts/*/snapshots", "/api/payments/webhook", "/api/webhooks/github", "/api/notices", "/api/donations", "/api/users/**", "/ws/**", "/login/**", "/oauth2/**", "/actuator/health", "/api/dev/**", "/api/push/vapid-public-key", "/mcp/**", "/api/featured-repos").permitAll()
                .requestMatchers("/actuator/metrics/**", "/actuator/info").hasRole("ADMIN")
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            // 이 백엔드는 뷰를 렌더링하지 않는 순수 API 서버라 인증 실패 응답을 받는 쪽은 항상 프론트 axios
            // 호출뿐이다(로그인 시작은 permitAll된 /oauth2/**로 직접 이동하므로 이 지점에 안 걸림). 그런데
            // oauth2Login()의 기본 AuthenticationEntryPoint는 302로 OAuth 인가 페이지(HTML)를 반환해,
            // axios가 이를 투명하게 따라가 res.data가 배열 대신 HTML 문자열이 되면서 .map() 호출 시 크래시로
            // 이어졌다(FE-22, TeamsPage 등 보호된 페이지 비로그인 접근 시 블랙 화면). 401 JSON으로 고정한다
            // — 프론트 axios 인터셉터(main.tsx)가 이미 401을 리프레시 토큰 재시도로 정상 처리하고 있었다.
            .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Unauthorized\"}");
            }))
            .oauth2Login(oauth2 -> oauth2
                .successHandler(oAuth2SuccessHandler)
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // 허용 Origin, 메서드, 헤더를 설정한 CORS 정책 반환
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("http://localhost:3000", "https://codeprint-iota.vercel.app"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        // /mcp/** — 브라우저 쿠키 인증이 아니라 AI 에이전트(Claude Code 등)가 로컬/원격에서 직접 호출하는
        // stateless JSON-RPC 엔드포인트. 호출자가 보내는 Origin이 프론트 도메인일 이유가 없어(CLI는 Origin이
        // 아예 없거나 임의 값) 위 브라우저용 화이트리스트를 적용하면 매번 403으로 막힘. 쿠키를 안 쓰므로
        // allowCredentials=false로 두고 모든 Origin 허용 — CsrfHeaderFilter의 /mcp/** 예외 처리와 동일한 이유.
        CorsConfiguration mcpConfig = new CorsConfiguration();
        mcpConfig.setAllowedOriginPatterns(List.of("*"));
        mcpConfig.setAllowedMethods(List.of("POST", "OPTIONS"));
        mcpConfig.setAllowedHeaders(List.of("*"));
        mcpConfig.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/mcp/**", mcpConfig);
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
