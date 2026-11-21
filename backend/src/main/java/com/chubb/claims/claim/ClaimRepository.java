package com.chubb.claims.claim;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ClaimRepository extends JpaRepository<Claim, Long> {

    Optional<Claim> findByClaimNumber(String claimNumber);

    /** Next value of the claim_number_seq Postgres sequence (used by {@link ClaimNumberGenerator}). */
    @Query(value = "SELECT nextval('claim_number_seq')", nativeQuery = true)
    long nextClaimNumberSequence();
}
