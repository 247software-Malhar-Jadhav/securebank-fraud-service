package com.securebank.fraud.scoring;

import lombok.Value;

import java.util.List;

/**
 * The result of running a single {@link FraudStrategy}.
 *
 * @param score   a partial risk contribution in [0.0, 1.0]
 * @param reasons human-readable reasons explaining the score (may be empty)
 */
@Value
public class StrategyOutcome {

    /** This strategy's risk contribution, clamped to [0.0, 1.0]. */
    double score;

    /** Why this strategy raised (or did not raise) risk; surfaced to the caller. */
    List<String> reasons;

    public static StrategyOutcome of(double score, List<String> reasons) {
        // Defensive clamp so a misbehaving strategy can never push the blended score
        // outside the documented 0..1 range.
        double clamped = Math.max(0.0, Math.min(1.0, score));
        return new StrategyOutcome(clamped, reasons == null ? List.of() : List.copyOf(reasons));
    }
}
