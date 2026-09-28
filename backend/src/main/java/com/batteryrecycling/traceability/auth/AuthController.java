package com.batteryrecycling.traceability.auth;

import com.batteryrecycling.traceability.auth.AuthDtos.LoginRequest;
import com.batteryrecycling.traceability.auth.AuthDtos.LoginResponse;
import com.batteryrecycling.traceability.common.api.ApiResponse;
import com.batteryrecycling.traceability.common.security.CurrentUserService;
import com.batteryrecycling.traceability.user.UserDto;
import com.batteryrecycling.traceability.user.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {
    private final AuthService authService;
    private final CurrentUserService currentUserService;
    private final UserService userService;

    public AuthController(AuthService authService, CurrentUserService currentUserService, UserService userService) {
        this.authService = authService;
        this.currentUserService = currentUserService;
        this.userService = userService;
    }

    @PostMapping("/api/v1/auth/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @GetMapping("/api/v1/auth/current-user")
    public ApiResponse<UserDto> currentUser() {
        return ApiResponse.ok(userService.toDto(currentUserService.requireCurrentUser().id()));
    }

    @GetMapping("/api/v1/auth/me")
    public ApiResponse<UserDto> me() {
        return currentUser();
    }
}
