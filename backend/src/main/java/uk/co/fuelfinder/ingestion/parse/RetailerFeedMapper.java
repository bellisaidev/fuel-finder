package uk.co.fuelfinder.ingestion.parse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import uk.co.fuelfinder.ingestion.normalize.NormalizedRecord;
import uk.co.fuelfinder.ingestion.parse.dto.RetailerFeedDto;
import uk.co.fuelfinder.ingestion.parse.dto.StationDto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class RetailerFeedMapper {

    private final ObjectMapper objectMapper;

    public RetailerFeedMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<NormalizedRecord> mapToNormalizedRecords(String rawJson) {
        final RetailerFeedDto feed;
        try {
            feed = objectMapper.readValue(rawJson, RetailerFeedDto.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse retailer feed JSON", e);
        }

        if (feed.getStations() == null || feed.getStations().isEmpty()) {
            return Collections.emptyList();
        }

        List<NormalizedRecord> out = new ArrayList<>();

        for (StationDto s : feed.getStations()) {
            if (s == null) continue;

            String siteId = trimToNull(s.getSiteId());
            if (siteId == null) continue;

            Double lat = s.getLatitude();
            Double lon = s.getLongitude();
            if (!isValidLatLon(lat, lon)) continue;

            Map<String, Integer> prices = s.getPrices();
            if (prices == null || prices.isEmpty()) continue;

            for (Map.Entry<String, Integer> entry : prices.entrySet()) {
                String fuelType = trimToNull(entry.getKey());
                Integer price = entry.getValue();

                if (fuelType == null) continue;
                if (price == null || price <= 0) continue;

                out.add(new NormalizedRecord(
                        siteId,
                        trimToNull(s.getBrand()),
                        trimToNull(s.getAddress()),
                        trimToNull(s.getPostcode()),
                        lat,
                        lon,
                        fuelType,
                        price
                ));
            }
        }

        return out;
    }

    private static boolean isValidLatLon(Double lat, Double lon) {
        if (lat == null || lon == null) return false;
        if (lat.isNaN() || lon.isNaN()) return false;
        if (lat < -90.0 || lat > 90.0) return false;
        if (lon < -180.0 || lon > 180.0) return false;
        if (lat == 0.0 && lon == 0.0) return false;
        return true;
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
