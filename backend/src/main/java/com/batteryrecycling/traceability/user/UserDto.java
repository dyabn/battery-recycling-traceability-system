package com.batteryrecycling.traceability.user;

import java.util.List;

public record UserDto(
        Long id,
        Long enterpriseId,
        String username,
        String displayName,
        String enabledStatus,
        List<String> roles,
        List<String> permissions
) {
}
