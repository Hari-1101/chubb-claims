package com.chubb.claims.common;

/**
 * The authenticated caller of the current request, resolved from the X-User-Id / X-User-Role headers.
 * Declare a {@code CurrentUser} parameter on any controller method to receive it.
 */
public record CurrentUser(String id, String name, UserRole role, String team) {

    public static CurrentUser from(AppUser user) {
        return new CurrentUser(user.getId(), user.getName(), user.getRole(), user.getTeam());
    }

    public boolean isStaff() {
        return role == UserRole.STAFF;
    }

    public boolean isClaimant() {
        return role == UserRole.CLAIMANT;
    }
}
