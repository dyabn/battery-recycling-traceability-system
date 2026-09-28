package com.batteryrecycling.traceability.auth;

import com.batteryrecycling.traceability.user.UserDto;
import jakarta.validation.constraints.NotBlank;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record LoginResponse(
            String accessToken,
            String tokenType,
            long expiresIn,
            UserDto currentUser
    ) {
    }
}

