package com.chubb.claims.claim;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/** The @EntityGraph annotations load each claim's policy in the same query (no N+1 when mapping to DTOs). */
public interface ClaimRepository extends JpaRepository<Claim, Long>, JpaSpecificationExecutor<Claim> {

    @EntityGraph(attributePaths = "policy")
    Optional<Claim> findByClaimNumber(String claimNumber);

    /** A claimant's claims, newest first. */
    @EntityGraph(attributePaths = "policy")
    List<Claim> findByPolicyHolderIdOrderByCreatedAtDescIdDesc(String holderId);

    /** Staff work queue (filters built by {@link ClaimQueueFilters}). */
    @Override
    @EntityGraph(attributePaths = "policy")
    Page<Claim> findAll(Specification<Claim> spec, Pageable pageable);

    /** Next value of the claim_number_seq Postgres sequence (used by {@link ClaimNumberGenerator}). */
    @Query(value = "SELECT nextval('claim_number_seq')", nativeQuery = true)
    long nextClaimNumberSequence();
}
