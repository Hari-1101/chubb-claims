package com.chubb.claims.claim;

import com.chubb.claims.common.Market;

import java.math.BigDecimal;

public record PolicyResponse(String policyNumber, Market market, ProductType productType, String currency,
                             BigDecimal coverageLimit) {
}
