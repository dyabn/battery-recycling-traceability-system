package com.batteryrecycling.traceability.config;

import com.batteryrecycling.traceability.audit.AuditService;
import com.batteryrecycling.traceability.common.api.ApiErrorResponse;
import com.batteryrecycling.traceability.common.exception.ApiException;
import com.batteryrecycling.traceability.common.security.CurrentUserService;
import com.batteryrecycling.traceability.common.security.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;
    private final CurrentUserService currentUserService;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            ObjectMapper objectMapper,
            AuditService auditService,
            CurrentUserService currentUserService
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
        this.currentUserService = currentUserService;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            ApiException apiException = (ApiException) request.getAttribute("authException");
                            String code = apiException == null ? "UNAUTHENTICATED" : apiException.code();
                            String message = apiException == null ? "请先登录" : apiException.getMessage();
                            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, code, message);
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            currentUserService.currentUser().ifPresent(user -> auditService.recordRejected(
                                    user.enterpriseId(),
                                    user.id(),
                                    request.getMethod() + " " + request.getRequestURI(),
                                    "HTTP_REQUEST",
                                    null,
                                    "FORBIDDEN",
                                    "没有执行该操作的权限",
                                    request
                            ));
                            writeError(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "没有执行该操作的权限");
                        })
                )
                .authorizeHttpRequests(registry -> registry
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/info",
                                "/api/v1/health",
                                "/api/v1/auth/login",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private void writeError(HttpServletResponse response, int status, String code, String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), new ApiErrorResponse(code, message, UUID.randomUUID().toString(), OffsetDateTime.now(), null));
    }
}
