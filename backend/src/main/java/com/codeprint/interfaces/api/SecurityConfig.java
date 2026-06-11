// Spring Security 설정 (JWT 필터, OAuth2, CORS)
package com.codeprint.interfaces.api;

import com.codeprint.infrastructure.security.JwtAuthenticationFilter;
import com.codeprint.infrastructure.security.OAuth2SuccessHandler;
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
                .requestMatchers("/api/auth/**", "/api/share/**", "/api/community/posts", "/api/community/posts/*/graph", "/api/payments/webhook", "/api/notices", "/api/donations", "/api/users/**", "/ws/**", "/login/**", "/oauth2/**", "/actuator/health", "/api/dev/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                .successHandler(oAuth2SuccessHandler)
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

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

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
