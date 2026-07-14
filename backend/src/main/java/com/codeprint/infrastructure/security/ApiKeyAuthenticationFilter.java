// 요청마다 팀 API 키(cpk_ 접두사)를 검증하여 인증 컨텍스트를 설정하는 필터
package com.codeprint.infrastructure.security;

import com.codeprint.domain.team.TeamApiKey;
import com.codeprint.domain.team.TeamApiKeyPrincipal;
import com.codeprint.domain.team.TeamApiKeyRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String KEY_PREFIX = "cpk_";

    private final TeamApiKeyRepository apiKeyRepository;

    // 요청마다 cpk_ 토큰을 검증하고 팀 스코프 인증 컨텍스트를 설정 (JWT는 JwtAuthenticationFilter가 별도 처리)
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (StringUtils.hasText(token) && token.startsWith(KEY_PREFIX)) {
            apiKeyRepository.findByKeyHash(TeamApiKey.hash(token))
                    .filter(key -> key.matches(token))
                    .ifPresent(key -> {
                        key.recordUsage(Instant.now());
                        apiKeyRepository.save(key);
                        var principal = new TeamApiKeyPrincipal(key.getTeamId(), key.getId());
                        var authentication = new UsernamePasswordAuthenticationToken(
                                principal, null, List.of(new SimpleGrantedAuthority("ROLE_TEAM_API_KEY")));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    });
        }

        filterChain.doFilter(request, response);
    }

    // Authorization 헤더에서 Bearer 토큰 추출
    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
