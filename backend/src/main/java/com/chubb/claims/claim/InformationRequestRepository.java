package com.chubb.claims.claim;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InformationRequestRepository extends JpaRepository<InformationRequest, Long> {

    List<InformationRequest> findByClaimOrderByRequestedAtAscIdAsc(Claim claim);

    /** Finds a request only if it belongs to the given claim. */
    Optional<InformationRequest> findByIdAndClaim(Long id, Claim claim);
}
