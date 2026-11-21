package com.chubb.claims.notification;

import java.time.Instant;

public record NotificationResponse(Long id, String claimNumber, String message, Instant createdAt) {
}
