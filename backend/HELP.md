# Backend Notes

The main project documentation lives in the repository root [README.md](../README.md).

This file is intentionally short and focused on backend-specific quick references.

## Run Profiles

- `local`: normal local development profile, scheduler enabled
- `local-manual`: manual ingestion profile, scheduler disabled, `IngestionRunner` executes once on startup
- `prod`: production-oriented settings
- `test`: lightweight profile used by Spring context tests that do not need the normal datasource/JPA/Flyway auto-configuration

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

From a clean checkout, execute the tests before coverage verification:

```powershell
.\gradlew.bat test
.\gradlew.bat jacocoTestCoverageVerification
```

```powershell
.\gradlew.bat bootJar
```

```powershell
docker build -t fuel-finder-backend:local .
```

CI details are documented in the repository root [README.md](../README.md).

## Production Configuration

Production runtime requirements, mandatory environment variables, defaults, and secret-handling guidance are documented in the root [README.md](../README.md#production-runtime-configuration).

## HTTP Resilience

The OAuth and Fuel Finder API clients share a bounded Reactor Netty connection pool. Runtime defaults under `fuelfinder.api.http` provide:

- `5s` connection and `20s` response timeouts
- a maximum of `20` pooled connections and `40` pending acquisitions
- up to `2` retries with exponential backoff and jitter
- bounded `Retry-After` handling for HTTP 429 and 503
- distinct connectivity, authentication, HTTP integration, and unexpected-failure mapping

These defaults can be overridden through Spring configuration or the optional environment variables documented in [Fuel Finder HTTP resilience](../README.md#fuel-finder-http-resilience) and [Production Runtime Configuration](../README.md#optional-http-resilience-overrides).

## Actuator and Observability

The base and production configurations expose:

- `/actuator/health`
- `/actuator/info`
- `/actuator/prometheus`

The Prometheus endpoint is intended for private monitoring access and must not be published as a public application endpoint.

From the repository root, start the optional local monitoring services while keeping the default Compose workflow database-only:

```powershell
docker compose --profile observability up -d
```

- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`
- Dashboard: **Fuel Finder / Fuel Finder Overview**

Stop only Prometheus and Grafana, leaving PostgreSQL running:

```powershell
docker compose --profile observability stop prometheus grafana
```

See [Metrics and Local Observability](../README.md#metrics-and-local-observability) for the custom meter catalogue, framework-provided metrics, dashboard scope, and timestamp semantics.

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

Run tests that follow the `*IT` naming convention from [`backend/`](.):

```powershell
.\gradlew.bat test --tests "*IT"
```

This does not select integration-style classes named `*IntegrationTest`. Run the regular task for the complete suite:

```powershell
.\gradlew.bat test
```
