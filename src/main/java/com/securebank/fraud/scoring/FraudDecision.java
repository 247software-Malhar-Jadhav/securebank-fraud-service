package com.securebank.fraud.scoring;

import lombok.Value;

import java.util.List;

/**
 * The blended scoring result returned by {@link FraudScoringService}: a 0..1 score, an
 * ALLOW/REVIEW/BLOCK decision, and the aggregated reasons from every strategy.
 */
@Value
public class FraudDecision {

    /** Blended risk score in [0.0, 1.0]. */
    double score;

    /** ALLOW | REVIEW | BLOCK. */
    String decision;

    /** Combined, de-duplicated reasons from all contributing strategies. */
    List<String> reasons;
}
