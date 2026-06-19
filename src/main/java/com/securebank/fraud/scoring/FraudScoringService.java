package com.securebank.fraud.scoring;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Orchestrator for the STRATEGY pattern: it owns the set of {@link FraudStrategy} beans,
 * blends their outcomes into a single 0..1 score, and turns that score into an
 * ALLOW / REVIEW / BLOCK decision with consolidated reasons.
 *
 * <p>The orchestrator deliberately knows nothing about HOW any strategy scores — it only
 * combines results. That is the whole point of Strategy: adding a third algorithm later
 * (e.g. an ML model) is a new {@code @Component} and zero changes here.</p>
 *
 * <p>Blending = weighted average using each strategy's {@link FraudStrategy#weight()}.</p>
 */
@Slf4j
@Service
public class FraudScoringService {

    /** Score >= this -> BLOCK. */
    private static final double BLOCK_THRESHOLD = 0.7;
    /** Score >= this (and < BLOCK) -> REVIEW. Below this -> ALLOW. */
    private static final double REVIEW_THRESHOLD = 0.4;

    private final List<FraudStrategy> strategies;

    /**
     * Constructor injection of ALL strategy beans. Spring supplies the full list, so the
     * blend automatically includes every {@link FraudStrategy} on the classpath.
     */
    public FraudScoringService(List<FraudStrategy> strategies) {
        this.strategies = List.copyOf(strategies);
        log.info("FraudScoringService initialised with {} strategies: {}",
                strategies.size(), strategies.stream().map(FraudStrategy::name).toList());
    }

    public FraudDecision score(ScoringContext ctx) {
        double weightedSum = 0.0;
        double weightTotal = 0.0;
        // LinkedHashSet keeps reasons ordered and de-duplicated across strategies.
        Set<String> reasons = new LinkedHashSet<>();

        for (FraudStrategy strategy : strategies) {
            StrategyOutcome outcome = strategy.evaluate(ctx);
            weightedSum += outcome.getScore() * strategy.weight();
            weightTotal += strategy.weight();
            // Prefix each reason with the strategy name for traceability.
            outcome.getReasons().forEach(r -> reasons.add("[" + strategy.name() + "] " + r));
        }

        double blended = weightTotal == 0.0 ? 0.0 : weightedSum / weightTotal;
        String decision = decide(blended);

        log.info("Score for customer={} amount={} -> blended={} decision={}",
                ctx.getCustomerId(), ctx.getAmount(), String.format("%.3f", blended), decision);

        return new FraudDecision(blended, decision, new ArrayList<>(reasons));
    }

    /** Map the blended score to the three-way decision. */
    private String decide(double score) {
        if (score >= BLOCK_THRESHOLD) {
            return "BLOCK";
        }
        if (score >= REVIEW_THRESHOLD) {
            return "REVIEW";
        }
        return "ALLOW";
    }
}
