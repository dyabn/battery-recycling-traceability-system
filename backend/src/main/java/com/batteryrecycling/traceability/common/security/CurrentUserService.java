package com.batteryrecycling.traceability.common.security;

import com.batteryrecycling.traceability.common.exception.ApiException;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    public CurrentUser requireCurrentUser() {
        return currentUser()
                .orElseThrow(() -> ApiException.unauthenticated("UNAUTHENTICATED", "请先登录"));
    }

    public Optional<CurrentUser> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUser currentUser)) {
            return Optional.empty();
        }
        return Optional.of(currentUser);
    }
}

