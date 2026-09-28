package com.batteryrecycling.traceability.role;

import java.util.Set;

public record RoleDto(
        Long id,
        String roleCode,
        String roleName,
        Set<String> permissions
) {
}

