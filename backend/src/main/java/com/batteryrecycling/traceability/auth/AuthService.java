package com.batteryrecycling.traceability.auth;

import com.batteryrecycling.traceability.auth.AuthDtos.LoginRequest;
import com.batteryrecycling.traceability.auth.AuthDtos.LoginResponse;
import com.batteryrecycling.traceability.common.exception.ApiException;
import com.batteryrecycling.traceability.common.security.CurrentUser;
import com.batteryrecycling.traceability.common.security.JwtService;
import com.batteryrecycling.traceability.user.UserService;
import com.batteryrecycling.traceability.user.UserService.UserAccount;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserService userService, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {
        UserAccount account = userService.findAccountByUsername(request.username());
        if (!"ENABLED".equals(account.enabledStatus())) {
            throw ApiException.unauthenticated("UNAUTHENTICATED", "用户已被禁用");
        }
        if (!passwordEncoder.matches(request.password(), account.passwordHash())) {
            throw ApiException.unauthenticated("UNAUTHENTICATED", "用户名或密码错误");
        }
        CurrentUser currentUser = userService.loadCurrentUser(account.id(), account.enterpriseId());
        JwtService.TokenResult token = jwtService.issueToken(currentUser);
        return new LoginResponse(token.accessToken(), token.tokenType(), token.expiresIn(), userService.toDto(account.id()));
    }
}

