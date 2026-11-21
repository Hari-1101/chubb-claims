package com.chubb.claims.claim;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/** A question from claims staff to the claimant about a claim. Open while it has no response. */
@Entity
@Table(name = "information_request")
public class InformationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false)
    private Claim claim;

    @Column(nullable = false, length = 2000)
    private String question;

    @Column(length = 2000)
    private String response;

    @Column(name = "requested_by", nullable = false, length = 20)
    private String requestedBy;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    protected InformationRequest() {
        // for JPA
    }

    public InformationRequest(Claim claim, String question, String requestedBy) {
        this.claim = Objects.requireNonNull(claim, "claim");
        this.question = Objects.requireNonNull(question, "question");
        this.requestedBy = Objects.requireNonNull(requestedBy, "requestedBy");
        this.requestedAt = Instant.now();
    }

    public boolean isOpen() {
        return response == null;
    }

    public Long getId() {
        return id;
    }

    public Claim getClaim() {
        return claim;
    }

    public String getQuestion() {
        return question;
    }

    public String getResponse() {
        return response;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }
}
