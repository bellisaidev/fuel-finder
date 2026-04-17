package uk.co.fuelfinder.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PriceUtils {

    private static final BigDecimal GBP_PRICE_THRESHOLD = BigDecimal.TEN;
    private static final BigDecimal PENCE_MULTIPLIER = BigDecimal.valueOf(100);

    private PriceUtils() {
    }

    public static int toPence(BigDecimal price) {
        if (price == null) {
            throw new IllegalArgumentException("Price cannot be null");
        }

        BigDecimal normalized = price.compareTo(GBP_PRICE_THRESHOLD) < 0
                ? price.multiply(PENCE_MULTIPLIER)
                : price;

        return normalized
                .setScale(0, RoundingMode.HALF_UP)
                .intValueExact();
    }
}
