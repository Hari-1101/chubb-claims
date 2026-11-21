package com.chubb.claims.claim;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ChangeReserveRequest(
        @NotNull @Positive @Digits(integer = 13, fraction = 2) BigDecimal amount,
        @NotBlank @Size(max = 500) String reason) {
}
