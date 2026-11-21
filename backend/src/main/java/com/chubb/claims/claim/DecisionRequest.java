package com.chubb.claims.claim;

import jakarta.validation.constraints.Size;

/** Body for approve. The reason is optional. */
public record DecisionRequest(@Size(max = 500) String reason) {
}
