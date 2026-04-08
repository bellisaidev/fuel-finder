package uk.co.fuelfinder.ingestion.normalize;

import org.springframework.stereotype.Component;
import uk.co.fuelfinder.ingestion.raw.client.dto.PfsLocationDto;
import uk.co.fuelfinder.ingestion.raw.client.dto.PfsStationDto;

@Component
public class PfsStationNormalizer {

    public NormalizedStation normalize(PfsStationDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("PfsStationDto cannot be null");
        }

        PfsLocationDto location = dto.location();

        return NormalizedStation.builder()
                .siteId(trimToNull(dto.nodeId()))
                .brand(resolveBrand(dto))
                .address(null)
                .postcode(null)
                .latitude(location != null ? location.latitude() : null)
                .longitude(location != null ? location.longitude() : null)
                .active(isActive(dto))
                .build();
    }

    private String resolveBrand(PfsStationDto dto) {
        String brandName = trimToNull(dto.brandName());
        if (brandName != null) {
            return brandName;
        }

        return trimToNull(dto.tradingName());
    }

    private boolean isActive(PfsStationDto dto) {
        return !Boolean.TRUE.equals(dto.permanentClosure())
                && !Boolean.TRUE.equals(dto.temporaryClosure());
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}