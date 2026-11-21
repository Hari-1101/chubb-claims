package com.chubb.claims.common;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppUserRepository extends JpaRepository<AppUser, String> {

    List<AppUser> findByRoleOrderById(UserRole role);
}
