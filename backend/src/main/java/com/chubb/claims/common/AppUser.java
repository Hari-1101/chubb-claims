package com.chubb.claims.common;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;

/**
 * A user of the platform: a claimant (policy holder) or a member of claims staff.
 * The id is what callers send in the X-User-Id header. Team is set for staff only.
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @Column(length = 20)
    private String id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(length = 50)
    private String team;

    protected AppUser() {
        // for JPA
    }

    public AppUser(String id, String name, String email, UserRole role, String team) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.email = Objects.requireNonNull(email, "email");
        this.role = Objects.requireNonNull(role, "role");
        if (role == UserRole.STAFF && team == null) {
            throw new BusinessRuleException("Staff user " + id + " must belong to a team");
        }
        if (role == UserRole.CLAIMANT && team != null) {
            throw new BusinessRuleException("Claimant " + id + " cannot belong to a team");
        }
        this.team = team;
    }

    public boolean isStaff() {
        return role == UserRole.STAFF;
    }

    public boolean isClaimant() {
        return role == UserRole.CLAIMANT;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public UserRole getRole() {
        return role;
    }

    public String getTeam() {
        return team;
    }
}
