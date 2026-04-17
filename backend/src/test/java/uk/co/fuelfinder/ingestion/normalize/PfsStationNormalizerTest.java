package uk.co.fuelfinder.ingestion.normalize;

import org.junit.jupiter.api.Test;
import uk.co.fuelfinder.ingestion.raw.client.dto.PfsLocationDto;
import uk.co.fuelfinder.ingestion.raw.client.dto.PfsStationDto;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

class PfsStationNormalizerTest {

    private final PfsStationNormalizer pfsStationNormalizer = new PfsStationNormalizer();

    @Test
    void normalizeMapsAddressPostcodeAndBrandFromPfsPayload() {
        PfsStationDto dto = new PfsStationDto(
                "site-1",
                null,
                "Shell Victoria",
                true,
                "SHELL",
                false,
                false,
                null,
                false,
                false,
                new PfsLocationDto(
                        "10 Downing Street",
                        "Westminster",
                        "London",
                        "UK",
                        "Greater London",
                        "SW1A 2AA",
                        51.5034,
                        -0.1276
                ),
                List.of(),
                null,
                List.of("E5", "E10")
        );

        NormalizedStation normalized = pfsStationNormalizer.normalize(dto);

        assertEquals("site-1", normalized.getSiteId());
        assertEquals("SHELL", normalized.getBrand());
        assertEquals("10 Downing Street", normalized.getAddress());
        assertEquals("London", normalized.getCity());
        assertEquals("Greater London", normalized.getCounty());
        assertEquals("UK", normalized.getCountry());
        assertEquals("SW1A 2AA", normalized.getPostcode());
        assertEquals(51.5034, normalized.getLatitude());
        assertEquals(-0.1276, normalized.getLongitude());
        assertTrue(normalized.isActive());
    }

    @Test
    void normalizeFallsBackToTradingNameAndHandlesMissingSecondAddressLine() {
        PfsStationDto dto = new PfsStationDto(
                " site-2 ",
                null,
                "BP Victoria",
                null,
                "   ",
                false,
                false,
                null,
                null,
                null,
                new PfsLocationDto(
                        "  Buckingham Palace Road ",
                        "   ",
                        "London",
                        "UK",
                        "Greater London",
                        " SW1W 0PP ",
                        51.4952,
                        -0.1439
                ),
                List.of(),
                null,
                List.of("E10")
        );

        NormalizedStation normalized = pfsStationNormalizer.normalize(dto);

        assertEquals("site-2", normalized.getSiteId());
        assertEquals("BP Victoria", normalized.getBrand());
        assertEquals("Buckingham Palace Road", normalized.getAddress());
        assertEquals("London", normalized.getCity());
        assertEquals("Greater London", normalized.getCounty());
        assertEquals("UK", normalized.getCountry());
        assertEquals("SW1W 0PP", normalized.getPostcode());
    }

    @Test
    void normalizeReturnsNullAddressAndPostcodeWhenLocationIsMissing() {
        PfsStationDto dto = new PfsStationDto(
                "site-3",
                null,
                "Esso Test",
                null,
                "ESSO",
                false,
                false,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                List.of()
        );

        NormalizedStation normalized = pfsStationNormalizer.normalize(dto);

        assertNull(normalized.getAddress());
        assertNull(normalized.getCity());
        assertNull(normalized.getCounty());
        assertNull(normalized.getCountry());
        assertNull(normalized.getPostcode());
    }
}
