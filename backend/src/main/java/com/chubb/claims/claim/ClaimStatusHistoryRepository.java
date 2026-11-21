package com.chubb.claims.claim;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimStatusHistoryRepository extends JpaRepository<ClaimStatusHistory, Long> {
}
