package com.securebank.fraud.scoring;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

/**
 * Immutable input bundle handed to every {@link FraudStrategy}.
 *
 * <p>It carries the transaction being scored plus the small amount of recent history the
 * strategies need (velocity, baseline statistics). In production this history would be
 * fetched from the ledger read-model of transaction-service; here it is supplied by an
 * in-memory demo dataset (see {@code DemoDataStore}) so the service runs standalone.</p>
 */
@Value
@Builder
public class ScoringContext {

    /** Source account of the transfer. */
    String fromAccountId;

    /** Destination account of the transfer. */
    String toAccountId;

    /** Owning customer. */
    String customerId;

    /** Transfer amount as BigDecimal (already converted from wire Money). */
    BigDecimal amount;

    /** ISO currency of the amount. */
    String currency;

    /** Whether the destination payee has been seen before for this customer. */
    boolean newPayee;

    /** Count of transfers this customer has made in the recent velocity window. */
    int recentTransferCount;

    /**
     * The customer's recent transfer amounts, used by the statistical strategy to build a
     * mean/standard-deviation baseline and compute a z-score for the current amount.
     */
    List<BigDecimal> recentAmounts;
}
