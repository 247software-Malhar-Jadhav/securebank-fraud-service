package com.securebank.fraud.scoring;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the STRATEGY-based scoring. We construct the orchestrator with the real
 * strategy beans (no Spring context needed) and assert the blended decisions.
 */
class FraudScoringServiceTest {

    private final FraudScoringService service = new FraudScoringService(
            List.of(new RuleBasedFraudStrategy(), new StatisticalFraudStrategy()));

    @Test
    void smallFamiliarTransfer_isAllowed() {
        // Amount in line with a stable history, known payee, low velocity -> ALLOW.
        ScoringContext ctx = ScoringContext.builder()
                .fromAccountId("a").toAccountId("acc-friend-1").customerId("cust-001")
                .amount(new BigDecimal("1000")).currency("INR")
                .newPayee(false).recentTransferCount(2)
                .recentAmounts(List.of(new BigDecimal("1000"), new BigDecimal("1100"),
                        new BigDecimal("950"), new BigDecimal("1050")))
                .build();

        FraudDecision d = service.score(ctx);
        assertEquals("ALLOW", d.getDecision());
        assertTrue(d.getScore() < 0.4, "expected low score, got " + d.getScore());
    }

    @Test
    void veryLargeNewPayeeHighVelocity_isBlocked() {
        // Very large amount + new payee + high velocity + statistical anomaly -> BLOCK.
        ScoringContext ctx = ScoringContext.builder()
                .fromAccountId("a").toAccountId("acc-unknown").customerId("cust-001")
                .amount(new BigDecimal("500000")).currency("INR")
                .newPayee(true).recentTransferCount(9)
                .recentAmounts(List.of(new BigDecimal("1000"), new BigDecimal("1100"),
                        new BigDecimal("950"), new BigDecimal("1050")))
                .build();

        FraudDecision d = service.score(ctx);
        assertEquals("BLOCK", d.getDecision());
        assertTrue(d.getScore() >= 0.7, "expected high score, got " + d.getScore());
        assertTrue(d.getReasons().stream().anyMatch(r -> r.contains("Very large amount")));
    }

    @Test
    void moderateAnomaly_isReviewed() {
        // A statistically unusual amount, new payee, but not extreme -> REVIEW range.
        ScoringContext ctx = ScoringContext.builder()
                .fromAccountId("a").toAccountId("acc-new").customerId("cust-x")
                .amount(new BigDecimal("60000")).currency("INR")
                .newPayee(true).recentTransferCount(3)
                .recentAmounts(List.of(new BigDecimal("1000"), new BigDecimal("1100"),
                        new BigDecimal("950"), new BigDecimal("1050")))
                .build();

        FraudDecision d = service.score(ctx);
        assertTrue(d.getScore() >= 0.4,
                "expected at least REVIEW-level score, got " + d.getScore());
    }
}
