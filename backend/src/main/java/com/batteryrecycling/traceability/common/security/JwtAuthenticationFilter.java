package com.batteryrecycling.traceability.common.security;

import com.batteryrecycling.traceability.common.exception.ApiException;
import com.batteryrecycling.traceability.tenant.TenantContext;
import com.batteryrecycling.traceability.user.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserService userService;

    public JwtAuthenticationFilter(JwtService jwtService, UserService userService) {
        this.jwtService = jwtService;
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                String token = header.substring(7);
                JwtService.JwtPrincipal principal = jwtService.parse(token);
                CurrentUser currentUser = userService.loadCurrentUser(principal.userId(), principal.enterpriseId());
                TenantContext.setEnterpriseId(currentUser.enterpriseId());
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        currentUser,
                        null,
                        currentUser.permissions().stream().map(SimpleGrantedAuthority::new).toList()
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
            filterChain.doFilter(request, response);
        } catch (ApiException exception) {
            request.setAttribute("authException", exception);
            SecurityContextHolder.clearContext();
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
