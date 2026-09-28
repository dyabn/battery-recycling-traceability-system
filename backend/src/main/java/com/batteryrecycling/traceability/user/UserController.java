package com.batteryrecycling.traceability.user;

import com.batteryrecycling.traceability.common.api.ApiResponse;
import com.batteryrecycling.traceability.common.security.CurrentUser;
import com.batteryrecycling.traceability.common.security.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

@RestController
@Validated
public class UserController {
    private final UserService userService;
    private final CurrentUserService currentUserService;

    public UserController(UserService userService, CurrentUserService currentUserService) {
        this.userService = userService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/api/v1/users")
    @PreAuthorize("hasAuthority('permission:manage')")
    public ApiResponse<List<UserDto>> listUsers(@RequestParam(required = false) Long enterpriseId, HttpServletRequest request) {
        CurrentUser currentUser = currentUserService.requireCurrentUser();
        return ApiResponse.ok(userService.listUsers(currentUser, enterpriseId, request));
    }

    @PutMapping("/api/v1/users/{id}/roles")
    @PreAuthorize("hasAuthority('permission:manage')")
    public ApiResponse<UserDto> updateUserRoles(
            @PathVariable Long id,
            @RequestHeader("Idempotency-Key") @Size(min = 8, max = 128) String idempotencyKey,
            @Valid @RequestBody UpdateUserRolesRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.ok(userService.updateUserRoles(currentUserService.requireCurrentUser(), id, request.roleCodes(), idempotencyKey, servletRequest));
    }

    public record UpdateUserRolesRequest(@NotEmpty Set<String> roleCodes) {
    }
}
