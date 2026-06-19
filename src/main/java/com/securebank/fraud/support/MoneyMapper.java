package com.securebank.fraud.support;

import com.securebank.contracts.common.v1.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Converts the wire {@link Money} (currency + int64 units + int32 nanos) to/from
 * {@link BigDecimal}, exactly as mandated by common.proto:
 * {@code BigDecimal(units + nanos/1e9)} with scale 4. We never use floating point for money.
 *
 * <p>This is a small, stateless utility (no state, pure functions) so it lives as static
 * helpers rather than a Spring bean.</p>
 */
public final class MoneyMapper {

    private static final BigDecimal NANOS_PER_UNIT = BigDecimal.valueOf(1_000_000_000L);

    private MoneyMapper() {
        // utility class
    }

    /** Wire Money -> BigDecimal with scale 4 (banker-safe rounding). */
    public static BigDecimal toBigDecimal(Money money) {
        if (money == null) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_EVEN);
        }
        BigDecimal units = BigDecimal.valueOf(money.getUnits());
        BigDecimal fraction = BigDecimal.valueOf(money.getNanos()).divide(NANOS_PER_UNIT);
        return units.add(fraction).setScale(4, RoundingMode.HALF_EVEN);
    }

    /** BigDecimal -> wire Money, splitting into whole units + nanos. */
    public static Money toMoney(String currency, BigDecimal amount) {
        BigDecimal normalised = amount.setScale(9, RoundingMode.HALF_EVEN);
        long units = normalised.longValue();
        int nanos = normalised.subtract(BigDecimal.valueOf(units))
                .multiply(NANOS_PER_UNIT)
                .intValue();
        return Money.newBuilder()
                .setCurrency(currency == null ? "INR" : currency)
                .setUnits(units)
                .setNanos(nanos)
                .build();
    }
}
