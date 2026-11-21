package com.chubb.claims.claim;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body for reject. A reason is always required. */
public record RejectClaimRequest(@NotBlank @Size(max = 500) String reason) {
}
