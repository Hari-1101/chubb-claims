package com.chubb.claims.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A message for a claimant about one of their claims. Written only by {@link NotificationService}. */
@Entity
@Table(name = "notification")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_id", nullable = false, length = 20)
    private String recipientId;

    @Column(name = "recipient_email", nullable = false)
    private String recipientEmail;

    @Column(name = "claim_number", nullable = false, length = 30)
    private String claimNumber;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Notification() {
        // for JPA
    }

    Notification(String recipientId, String recipientEmail, String claimNumber, String message, Instant createdAt) {
        this.recipientId = recipientId;
        this.recipientEmail = recipientEmail;
        this.claimNumber = claimNumber;
        this.message = message;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getRecipientId() {
        return recipientId;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public String getClaimNumber() {
        return claimNumber;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
