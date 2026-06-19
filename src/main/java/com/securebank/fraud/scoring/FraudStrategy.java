package com.securebank.fraud.scoring;

/**
 * STRATEGY pattern — the common interface for an interchangeable fraud-scoring algorithm.
 *
 * <p>Why Strategy: fraud detection is naturally a family of independent algorithms
 * (rule-based heuristics, statistical anomaly detection, ML models later) that all answer
 * the same question — "how risky is this transaction?" — in different ways. Coding them
 * behind one interface lets {@code FraudScoringService} blend any set of them without
 * knowing their internals, and lets us add/remove strategies (even via config) without
 * touching the orchestration logic.</p>
 *
 * <p>Each strategy is a Spring bean; the orchestrator receives all of them injected as a
 * {@code List<FraudStrategy>} and combines their outcomes.</p>
 */
public interface FraudStrategy {

    /** Stable name used in logs and to attribute reasons. */
    String name();

    /**
     * Relative weight of this strategy when blending into the final 0..1 score.
     * The orchestrator computes a weighted average using these weights.
     */
    double weight();

    /** Run the algorithm against the context and return its risk contribution + reasons. */
    StrategyOutcome evaluate(ScoringContext context);
}
