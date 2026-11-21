package com.chubb.claims.claim;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A claimant reports an incident. "Not in the future" for incidentDate is checked by the domain, because it depends
 * on the market's time zone.
 */
public record SubmitClaimRequest(
        @NotBlank @Size(max = 30) String policyNumber,
        @NotNull LocalDate incidentDate,
        @NotBlank @Size(max = 2000) String description,
        @NotNull @Positive @Digits(integer = 13, fraction = 2) BigDecimal estimatedAmount) {
}
