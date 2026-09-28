package com.batteryrecycling.traceability.user;

import java.util.Set;

public record UserDto(
        Long id,
        Long enterpriseId,
        String username,
        String displayName,
        String enabledStatus,
        Set<String> roles,
        Set<String> permissions
) {
}

