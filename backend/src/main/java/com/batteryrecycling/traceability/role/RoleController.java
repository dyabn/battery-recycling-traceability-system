package com.batteryrecycling.traceability.role;

import com.batteryrecycling.traceability.common.security.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RoleController {
    private final RoleService roleService;
    private final CurrentUserService currentUserService;

    public RoleController(RoleService roleService, CurrentUserService currentUserService) {
        this.roleService = roleService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/api/v1/roles")
    @PreAuthorize("hasAuthority('permission:manage')")
    public List<RoleDto> listRoles() {
        return roleService.listRoles();
    }

    @PutMapping("/api/v1/roles/{id}/permissions")
    @PreAuthorize("hasAuthority('permission:manage')")
    public RoleDto updateRolePermissions(
            @PathVariable Long id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody UpdateRolePermissionsRequest request,
            HttpServletRequest servletRequest
    ) {
        return roleService.updateRolePermissions(currentUserService.requireCurrentUser(), id, request.permissionCodes(), idempotencyKey, servletRequest);
    }

    public record UpdateRolePermissionsRequest(@NotEmpty Set<String> permissionCodes) {
    }
}

