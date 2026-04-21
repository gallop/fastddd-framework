package com.gallop.fastddd.security.jwt;

import com.gallop.fastddd.security.channel.AuthChannel;
import com.gallop.fastddd.security.channel.AuthChannelContext;
import com.gallop.fastddd.security.config.SecurityHttpProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final SecurityHttpProperties httpProperties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider,
                                   JwtProperties jwtProperties,
                                   SecurityHttpProperties httpProperties) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.jwtProperties = jwtProperties;
        this.httpProperties = httpProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && jwtTokenProvider.validateToken(jwt)) {
                String username = jwtTokenProvider.getUsernameFromToken(jwt);
                List<String> authorities = jwtTokenProvider.getAuthoritiesFromToken(jwt);
                AuthChannel tokenChannel = jwtTokenProvider.getChannelFromToken(jwt);

                // 通道校验：仅当应用配置了非 DEFAULT 通道时启用
                AuthChannel appChannel = jwtProperties.getChannel();
                if (appChannel != null && appChannel != AuthChannel.DEFAULT
                        && tokenChannel != appChannel && tokenChannel != AuthChannel.DEFAULT) {
                    log.warn("Channel mismatch: token channel={}, app channel={}, user={}",
                            tokenChannel, appChannel, username);
                    SecurityContextHolder.clearContext();
                    // 继续过滤器链，由 Spring Security 的 JwtAuthenticationEntryPoint 返回 401
                    filterChain.doFilter(request, response);
                    return;
                }

                // 校验通过后才设置通道上下文
                AuthChannelContext.set(tokenChannel);

                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    List<SimpleGrantedAuthority> grantedAuthorities = authorities.stream()
                            .map(SimpleGrantedAuthority::new)
                            .collect(Collectors.toList());

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(username, null, grantedAuthorities);
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    log.debug("Successfully authenticated user: {}, channel: {}", username, tokenChannel);
                }
            }

            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            log.error("Could not set user authentication in security context", ex);
            throw ex;
        } finally {
            AuthChannelContext.clear();
        }
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(jwtProperties.getHeaderName());
        return jwtTokenProvider.extractTokenFromHeader(bearerToken);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getServletPath();
        List<String> permitAllPaths = httpProperties.getPermitAllPaths();
        if (permitAllPaths != null) {
            for (String pattern : permitAllPaths) {
                if (pathMatcher.match(pattern, path)) {
                    return true;
                }
            }
        }
        return false;
    }
}
