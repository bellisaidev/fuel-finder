package uk.co.fuelfinder.common;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PriceUtilsTest {

    @Test
    void convertsDecimalPriceToPence() {
        assertEquals(139, PriceUtils.toPence(new BigDecimal("1.389")));
    }

    @Test
    void rejectsNullPrice() {
        assertThrows(IllegalArgumentException.class, () -> PriceUtils.toPence(null));
    }
}
