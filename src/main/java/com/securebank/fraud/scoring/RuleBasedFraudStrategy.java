package com.securebank.fraud.scoring;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * STRATEGY implementation #1 — deterministic rule-based heuristics.
 *
 * <p>Encodes the kind of hard rules a fraud analyst would write first:</p>
 * <ul>
 *   <li><b>Amount thresholds</b> — large transfers are inherently riskier.</li>
 *   <li><b>Velocity</b> — many transfers in a short window suggest account takeover.</li>
 *   <li><b>New payee</b> — first-ever transfer to a destination is riskier, more so when
 *       it is also a large amount.</li>
 * </ul>
 *
 * <p>Each triggered rule adds a bounded amount of risk and a human-readable reason. The
 * total is clamped to [0,1] by {@link StrategyOutcome}.</p>
 */
@Slf4j
@Component
public class RuleBasedFraudStrategy implements FraudStrategy {

    // --- Tunable thresholds. In production these would come from config/policy. ---
    /** Transfers at/above this are "large" and contribute meaningful risk. */
    private static final BigDecimal LARGE_AMOUNT = new BigDecimal("50000");
    /** Transfers at/above this are "very large" and contribute more. */
    private static final BigDecimal VERY_LARGE_AMOUNT = new BigDecimal("200000");
    /** More than this many recent transfers indicates suspicious velocity. */
    private static final int VELOCITY_LIMIT = 5;

    @Override
    public String name() {
        return "rule-based";
    }

    @Override
    public double weight() {
        // Rules are precise but coarse; give them slightly higher weight than statistics
        // because they encode explicit policy.
        return 0.6;
    }

    @Override
    public StrategyOutcome evaluate(ScoringContext ctx) {
        double risk = 0.0;
        List<String> reasons = new ArrayList<>();

        // Rule 1: amount thresholds.
        if (ctx.getAmount().compareTo(VERY_LARGE_AMOUNT) >= 0) {
            risk += 0.5;
            reasons.add("Very large amount (>= " + VERY_LARGE_AMOUNT + " " + ctx.getCurrency() + ")");
        } else if (ctx.getAmount().compareTo(LARGE_AMOUNT) >= 0) {
            risk += 0.25;
            reasons.add("Large amount (>= " + LARGE_AMOUNT + " " + ctx.getCurrency() + ")");
        }

        // Rule 2: velocity.
        if (ctx.getRecentTransferCount() > VELOCITY_LIMIT) {
            risk += 0.3;
            reasons.add("High velocity: " + ctx.getRecentTransferCount()
                    + " recent transfers (limit " + VELOCITY_LIMIT + ")");
        }

        // Rule 3: new payee (amplified when also a large amount).
        if (ctx.isNewPayee()) {
            double newPayeeRisk = ctx.getAmount().compareTo(LARGE_AMOUNT) >= 0 ? 0.3 : 0.15;
            risk += newPayeeRisk;
            reasons.add("First-time payee: " + ctx.getToAccountId());
        }

        log.debug("rule-based score for customer={} -> {} ({} reasons)",
                ctx.getCustomerId(), risk, reasons.size());
        return StrategyOutcome.of(risk, reasons);
    }
}
