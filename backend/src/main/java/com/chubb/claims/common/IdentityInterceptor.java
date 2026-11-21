package com.chubb.claims.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

/**
 * Fake authentication (real auth is out of scope). For every /api request:
 * <ol>
 *   <li>Reads X-User-Id and X-User-Role and loads the {@link AppUser}. Missing/unknown user or a role that doesn't
 *       match the user's real role → 401.</li>
 *   <li>Stores the {@link CurrentUser} on the request for {@link CurrentUserArgumentResolver}.</li>
 *   <li>Checks {@link RequireRole} on the endpoint → 403 if the role isn't allowed.</li>
 * </ol>
 */
@Component
public class IdentityInterceptor implements HandlerInterceptor {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLE_HEADER = "X-User-Role";
    static final String CURRENT_USER_ATTRIBUTE = CurrentUser.class.getName();

    private final AppUserRepository appUserRepository;

    public IdentityInterceptor(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Only controller methods need identity. This also lets CORS preflight (OPTIONS) requests through.
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        CurrentUser user = authenticate(request);
        request.setAttribute(CURRENT_USER_ATTRIBUTE, user);
        checkRole(method, user);
        return true;
    }

    private CurrentUser authenticate(HttpServletRequest request) {
        String userId = request.getHeader(USER_ID_HEADER);
        String roleHeader = request.getHeader(USER_ROLE_HEADER);
        if (userId == null || userId.isBlank()) {
            throw new UnauthorizedException("Missing " + USER_ID_HEADER + " header");
        }
        if (roleHeader == null || roleHeader.isBlank()) {
            throw new UnauthorizedException("Missing " + USER_ROLE_HEADER + " header");
        }
        UserRole role;
        try {
            role = UserRole.valueOf(roleHeader.trim());
        } catch (IllegalArgumentException e) {
            throw new UnauthorizedException(USER_ROLE_HEADER + " must be one of " + Arrays.toString(UserRole.values()));
        }
        AppUser user = appUserRepository.findById(userId.trim())
                .orElseThrow(() -> new UnauthorizedException("Unknown user " + userId.trim()));
        if (user.getRole() != role) {
            throw new UnauthorizedException(USER_ROLE_HEADER + " " + role + " does not match the role of user " + user.getId());
        }
        return CurrentUser.from(user);
    }

    private void checkRole(HandlerMethod method, CurrentUser user) {
        RequireRole rule = method.getMethodAnnotation(RequireRole.class);
        if (rule == null) {
            rule = method.getBeanType().getAnnotation(RequireRole.class);
        }
        if (rule != null && !Arrays.asList(rule.value()).contains(user.role())) {
            throw new ForbiddenException("This endpoint requires role " + Arrays.toString(rule.value()));
        }
    }
}
