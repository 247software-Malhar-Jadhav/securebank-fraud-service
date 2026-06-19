package com.securebank.fraud.insights;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * A small, in-memory demo dataset standing in for the ledger read-model.
 *
 * <p><b>Production note (documented as required):</b> this service deliberately has NO
 * relational database. In production, spending insights and the scoring history would be
 * computed by querying transaction-service's ledger read-model over gRPC. To keep this
 * repo independently buildable and runnable offline, we instead serve a tiny baked-in
 * dataset here. The shape (category -> amount, recent amounts per customer) mirrors what
 * the read-model would return, so swapping in a real client is a localized change.</p>
 */
@Component
public class DemoDataStore {

    /** customerId -> (category -> total spent). */
    private static final Map<String, Map<String, BigDecimal>> SPEND_BY_CUSTOMER = Map.of(
            "cust-001", Map.of(
                    "Groceries", new BigDecimal("12500.00"),
                    "Dining", new BigDecimal("8200.00"),
                    "Transport", new BigDecimal("4300.00"),
                    "Utilities", new BigDecimal("6100.00"),
                    "Shopping", new BigDecimal("15400.00")),
            "cust-002", Map.of(
                    "Groceries", new BigDecimal("9800.00"),
                    "Entertainment", new BigDecimal("5200.00"),
                    "Travel", new BigDecimal("31000.00"),
                    "Utilities", new BigDecimal("4500.00"))
    );

    /** customerId -> recent transfer amounts (for the statistical fraud baseline). */
    private static final Map<String, List<BigDecimal>> RECENT_AMOUNTS = Map.of(
            "cust-001", List.of(
                    new BigDecimal("1200"), new BigDecimal("800"), new BigDecimal("1500"),
                    new BigDecimal("950"), new BigDecimal("1100")),
            "cust-002", List.of(
                    new BigDecimal("5000"), new BigDecimal("4800"), new BigDecimal("5200"),
                    new BigDecimal("4900"))
    );

    /** A fallback baseline used for unknown customers (keeps Score() useful in demos). */
    private static final List<BigDecimal> DEFAULT_RECENT = List.of(
            new BigDecimal("1000"), new BigDecimal("1200"), new BigDecimal("900"),
            new BigDecimal("1100"));

    private static final Map<String, BigDecimal> DEFAULT_SPEND = Map.of(
            "Groceries", new BigDecimal("5000.00"),
            "Dining", new BigDecimal("3000.00"),
            "Transport", new BigDecimal("2000.00"));

    /** Category spend for a customer (or a generic default for unknown customers). */
    public Map<String, BigDecimal> spendByCategory(String customerId) {
        return SPEND_BY_CUSTOMER.getOrDefault(customerId, DEFAULT_SPEND);
    }

    /** Recent transfer amounts for a customer (or a generic default). */
    public List<BigDecimal> recentAmounts(String customerId) {
        return RECENT_AMOUNTS.getOrDefault(customerId, DEFAULT_RECENT);
    }

    /** Demo heuristic: a destination is a "known payee" only for a couple of seeded pairs. */
    public boolean isKnownPayee(String customerId, String toAccountId) {
        // Seed: cust-001 has previously paid acc-friend-1; everything else is "new".
        return "cust-001".equals(customerId) && "acc-friend-1".equals(toAccountId);
    }
}
