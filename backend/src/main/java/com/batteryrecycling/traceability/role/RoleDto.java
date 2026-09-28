package com.batteryrecycling.traceability.role;

import java.util.List;

public record RoleDto(
        Long id,
        String roleCode,
        String roleName,
        List<String> permissions
) {
}
