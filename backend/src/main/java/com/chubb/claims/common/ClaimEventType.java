package com.chubb.claims.common;

/**
 * Types of claim domain events. Constant names are the exact values sent on the wire ("eventType": "ClaimSubmitted").
 */
public enum ClaimEventType {
    ClaimSubmitted,
    ClaimAssigned,
    InformationRequested,
    InformationResponded,
    ReserveChanged,
    ClaimApproved,
    ClaimRejected,
    ClaimSettled
}
