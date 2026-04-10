package uk.co.fuelfinder.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PriceUtils {

    private static final BigDecimal PENCE_MULTIPLIER = BigDecimal.valueOf(100);

    private PriceUtils() {
    }

    public static int toPence(BigDecimal price) {
        if (price == null) {
            throw new IllegalArgumentException("Price cannot be null");
        }

        return price
                .multiply(PENCE_MULTIPLIER)
                .setScale(0, RoundingMode.HALF_UP)
                .intValueExact();
    }
}
