# Backend Notes

The main project documentation lives in the repository root [README.md](../README.md).

This file is intentionally short and focused on backend-specific quick references.

## Run Profiles

- `local`: normal local development profile, scheduler enabled
- `local-manual`: manual ingestion profile, scheduler disabled, `IngestionRunner` executes once on startup
- `prod`: production-oriented settings
- `test`: lightweight test profile used by `BackendApplicationTests`

## Useful Commands

From [`backend/`](.) on Windows PowerShell:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local-manual"
```

```powershell
.\gradlew.bat test
```

## Geospatial API

Available station endpoints:

- `GET /v1/stations/nearby`
- `GET /v1/stations/cheapest-nearby`
- `GET /v1/stations/in-bounds`
- `GET /v1/stations/{stationId}`
- `GET /v1/stations/{stationId}/price-history`
- `GET /v1/stations/{stationId}/price-history/summary`

Station query caching uses local Caffeine caches for:

- `nearbyStations`
- `cheapestNearbyStations`
- `inBoundsStations`
- `stationDetails`
- `stationPriceHistory`
- `stationPriceHistorySummary`

Nearby and cheapest-nearby endpoints accept:

- `lat`
- `lon`
- `radiusMeters`
- `fuelType`
- `limit` (optional, default `10`, max `100`)

In-bounds endpoint accepts:

- `bbox` required, formatted as `west,south,east,north`
- `fuelType`
- `limit` optional, default `250`, max `500`

Station details endpoint accepts:

- `stationId` as UUID path variable

Station price history endpoint accepts:

- `stationId` as UUID path variable
- `fuelType` required
- `from` optional ISO-8601 timestamp
- `to` optional ISO-8601 timestamp
- `limit` optional, default `100`, max `1000`

Station price history summary endpoint accepts:

- `stationId` as UUID path variable
- `fuelType` required
- `from` optional ISO-8601 timestamp
- `to` optional ISO-8601 timestamp
- `limit` optional, default `30`, max `365`

## Station Location Fields

The normalized `station` model now stores:

- `address` as `address_line_1`
- `city`
- `county`
- `country`
- `postcode`
- `location` as PostGIS geography point

These fields are populated from the PFS feed normalization flow and exposed by the geospatial API.

## Ingestion Reconciliation

Retailer ingestion logs a reconciliation summary after parsing and processing each batch.

Statuses:

- `OK`
- `OK_WITH_SKIPS`
- `FAILED`

Runtime action is configured separately:

```yaml
fuelfinder:
  ingestion:
    reconciliation:
      unexplained-mismatch-action: fail
```

Accepted values are `fail` and `warn`. In `warn` mode, a failed reconciliation still reports status `FAILED`; ingestion is allowed to continue.

## Integration Tests

Run all integration tests from [`backend/`](.):

```powershell
.\gradlew.bat test --tests "*IT"
```
