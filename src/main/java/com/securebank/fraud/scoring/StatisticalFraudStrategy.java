package com.securebank.fraud.scoring;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * STRATEGY implementation #2 — statistical anomaly detection via z-score.
 *
 * <p>It builds a baseline from the customer's recent transfer amounts (mean + sample
 * standard deviation) and measures how many standard deviations the current amount is
 * from that mean — the classic z-score. A spend wildly out of line with the customer's
 * own history is suspicious even if it is below any fixed rule threshold.</p>
 *
 * <p>The z-score is mapped to a bounded risk contribution: |z| >= 3 is treated as the top
 * of the range. With too little history to form a baseline, this strategy abstains
 * (returns 0) and lets the rule-based strategy carry the decision.</p>
 */
@Slf4j
@Component
public class StatisticalFraudStrategy implements FraudStrategy {

    /** Need at least this many samples before a baseline is meaningful. */
    private static final int MIN_SAMPLES = 3;
    /** z at/above this maps to maximum statistical risk. */
    private static final double Z_SATURATION = 3.0;

    @Override
    public String name() {
        return "statistical";
    }

    @Override
    public double weight() {
        return 0.4;
    }

    @Override
    public StrategyOutcome evaluate(ScoringContext ctx) {
        List<BigDecimal> history = ctx.getRecentAmounts();
        if (history == null || history.size() < MIN_SAMPLES) {
            // Not enough data to judge anomaly; abstain.
            return StrategyOutcome.of(0.0, List.of("Insufficient history for statistical baseline"));
        }

        double[] values = history.stream().mapToDouble(BigDecimal::doubleValue).toArray();
        double mean = mean(values);
        double stdDev = sampleStdDev(values, mean);

        List<String> reasons = new ArrayList<>();
        if (stdDev == 0.0) {
            // All historical amounts identical. Any difference is "infinitely" anomalous;
            // treat a non-equal amount as high risk, an equal amount as safe.
            double amount = ctx.getAmount().doubleValue();
            if (amount != mean) {
                reasons.add(String.format(
                        "Amount %.2f differs from a perfectly stable history (mean %.2f)", amount, mean));
                return StrategyOutcome.of(0.8, reasons);
            }
            return StrategyOutcome.of(0.0, List.of("Amount matches stable history"));
        }

        double z = Math.abs((ctx.getAmount().doubleValue() - mean) / stdDev);
        // Linear map |z| in [0, Z_SATURATION] -> risk in [0, 1].
        double risk = Math.min(1.0, z / Z_SATURATION);
        if (z >= 2.0) {
            reasons.add(String.format(
                    "Statistically anomalous amount: z-score %.2f vs baseline (mean %.2f, sd %.2f)",
                    z, mean, stdDev));
        } else {
            reasons.add(String.format("Amount within normal range (z-score %.2f)", z));
        }

        log.debug("statistical score for customer={} -> {} (z={})", ctx.getCustomerId(), risk, z);
        return StrategyOutcome.of(risk, reasons);
    }

    private static double mean(double[] xs) {
        double sum = 0;
        for (double x : xs) {
            sum += x;
        }
        return sum / xs.length;
    }

    /** Sample standard deviation (n-1 denominator). */
    private static double sampleStdDev(double[] xs, double mean) {
        if (xs.length < 2) {
            return 0.0;
        }
        double sumSq = 0;
        for (double x : xs) {
            double d = x - mean;
            sumSq += d * d;
        }
        return Math.sqrt(sumSq / (xs.length - 1));
    }
}
