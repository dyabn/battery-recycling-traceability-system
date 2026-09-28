package com.batteryrecycling.traceability.common.security;

import java.util.Set;

public record CurrentUser(
        Long id,
        Long enterpriseId,
        String username,
        String displayName,
        Set<String> roles,
        Set<String> permissions
) {
    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }
}

