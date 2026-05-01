# Fuel Finder

Fuel Finder is a backend Java/Spring Boot project for ingesting and storing data from the UK Fuel Finder Scheme.

The repository now covers both the ingestion pipeline and an initial geospatial read API: OAuth authentication, paginated feed retrieval, raw payload storage, station normalization, PostgreSQL/PostGIS persistence, and public station lookup endpoints.

## Current Status

What is implemented today:

- Spring Boot backend with Java 21
- PostgreSQL + PostGIS local environment via Docker Compose
- Flyway database migrations
- OAuth2 client credentials integration with the Fuel Finder API
- Paginated retrieval of PFS and fuel price feeds
- Raw feed persistence for auditability
- Station normalization and upsert flow
- Ingestion reconciliation checks for raw, normalized, skipped, duplicate, and persisted counts
- Fuel price normalization, deduplicated observation ingestion, and latest price projection/backfill
- Geospatial read APIs for nearby stations, cheapest nearby stations, map bounds, station details, and price history
- Local Caffeine caching for repeated geospatial read queries with transaction-safe invalidation after latest-price updates
- Global API error handling for invalid query parameters
- Lightweight operational logging for public station queries with success timing/result counts and consistent `400` warnings
- Station persistence enriched with `address`, `city`, `county`, `country`, and `postcode`
- Persistence model and schema for retailers, raw feeds, stations, price observations, and latest prices
- Unit tests with JUnit 5 and Mockito across auth, client, normalization, exception, and ingestion orchestration components
- Integration tests with Spring Boot Test and Testcontainers for JDBC persistence, end-to-end ingestion, deduplication, and station persistence flows

What is still in progress:

- Additional read filters and query shapes beyond the current station endpoints
- Broader integration coverage across more failure scenarios and ingestion edge cases
- Cleanup or consolidation of alternative JDBC write paths that are not part of the active ingestion flow

## Tech Stack

- Java 21
- Spring Boot 3
- Spring Web
- Spring WebFlux `WebClient`
- Spring Data JPA
- Spring Cache
- Hibernate Spatial
- PostgreSQL
- PostGIS
- Flyway
- Caffeine
- Docker Compose
- Lombok
- Testcontainers

## Architecture

The codebase is structured as a modular monolith with a backend-first focus.

Main areas:

- `config/`: Spring configuration, WebClient setup, and cache configuration/properties
- `ingestion/raw/auth/`: Fuel Finder API properties, OAuth clients, token management
- `ingestion/raw/client/`: external feed clients and DTOs
- `ingestion/raw/orchestrator/`: ingestion coordination
- `ingestion/raw/writer/`: raw payload storage plus experimental/alternative JDBC write helpers
- `ingestion/normalize/`: station normalization, price normalization, observation ingestion, and latest price projection
- `api/station/`: station read endpoints for nearby, cheapest-nearby, map bounds, details, and price history lookups
- `persistence/entity/`: JPA entities
- `persistence/repository/`: Spring Data repositories

### High-Level Flow

```mermaid
flowchart TD
    A[Fuel Finder API] --> B[OAuth token retrieval]
    B --> C[PFS client]
    B --> D[Fuel prices client]

    C --> E[PFS raw feed storage]
    E --> F[Station normalization]
    F --> G[Station upsert]
    G --> H[(station)]
    E --> I[(raw_feed_fetch)]

    D --> J[Fuel prices raw feed storage]
    J --> K[Price normalization]
    K --> L[Price observation write]
    L --> M[(price_observation)]
    L --> N[Latest price update]
    N --> O[(latest_price)]
    H --> P[Geospatial station query API]
    O --> P
    H -. station lookup / join .-> L
    J --> I
    G --> Q[Reconciliation summary]
    L --> Q
```

## Data Model

Core tables currently defined through Flyway:

- `retailer`: feed source registry
- `raw_feed_fetch`: raw JSON payloads and audit trail
- `station`: normalized station data with geo location and location metadata
- `price_observation`: append-only price history
- `latest_price`: read model for current price lookups
- `shedlock`: distributed scheduler lock table

Important design choices:

- raw external payloads are stored for traceability
- ingestion records reconciliation counts after raw payload parsing, normalization, skips, duplicate detection, and persistence outcomes
- spatial data uses PostGIS
- database migrations are source-controlled with Flyway
- the model separates historical observations from the latest-price read model
- geospatial API reads are served from `station` joined to `latest_price`
- repeated geospatial reads are cached in-memory to reduce repeated DB load

### Station Location Fields

The `station` model now persists:

- `address` from PFS `address_line_1`
- `city`
- `county`
- `country`
- `postcode`
- `location` as a PostGIS geography point

This keeps the primary street address simple while preserving the other location fields separately for future query and presentation needs.

## Ingestion Reconciliation

The ingestion orchestrator logs a reconciliation summary after each retailer batch has been parsed and processed.

Reconciliation has a factual status and a separate runtime action:

- `OK`: raw records reconcile with normalized and persisted outcomes, with no accounted skips
- `OK_WITH_SKIPS`: counts reconcile, but known skips or duplicates were recorded
- `FAILED`: at least one reconciliation formula has an unexplained mismatch

The runtime action controls what happens when status is `FAILED`:

- `FAIL`: aborts the retailer ingestion and returns a failed ingestion summary
- `WARN`: logs the failed reconciliation but lets the ingestion business outcome remain successful

Important distinction: in warn mode, the reconciliation status still remains `FAILED`; only the reaction policy changes.

### Count Levels

PFS station reconciliation is station-level:

```text
rawStationCount == normalizedStationCount + skippedCount
```

`skippedCount` is the aggregate used by the formula. The first specific skip reason tracked today is `skippedMissingSiteIdCount`.

Fuel price reconciliation is split into two separate levels:

```text
rawFuelPriceEntryCount == normalizedObservationCount + skippedInvalidUnusableEntryCount
```

```text
normalizedObservationCount == insertedCount + duplicateCount + missingStationCount + otherPersistenceSkipCount
```

This keeps raw-payload normalization accounting separate from persistence/business outcomes such as duplicates and missing stations.

### Reconciliation Logs

The structured reconciliation log includes:

- retailer
- raw feed fetch IDs
- PFS raw, normalized, skipped, and upsert counts
- fuel price raw entry, normalized observation, invalid/unusable skip, inserted, duplicate, missing-station, and other persistence-skip counts
- reconciliation status, configured action, abort decision, and message

## API Endpoints

Currently available read endpoints:

- `GET /v1/stations/nearby`
- `GET /v1/stations/cheapest-nearby`
- `GET /v1/stations/in-bounds`
- `GET /v1/stations/{stationId}`
- `GET /v1/stations/{stationId}/price-history`
- `GET /v1/stations/{stationId}/price-history/summary`

Nearby and cheapest-nearby endpoints accept:

- `lat`
- `lon`
- `radiusMeters`
- `fuelType`
- `limit` optional, default `10`, max `100`

In-bounds endpoint accepts:

- `bbox` required, formatted as `west,south,east,north`
- `fuelType`
- `limit` optional, default `250`, max `500`

Station details endpoint accepts:

- `stationId` path variable as UUID

Station price history endpoint accepts:

- `stationId` path variable as UUID
- `fuelType` required
- `from` optional ISO-8601 timestamp
- `to` optional ISO-8601 timestamp
- `limit` optional, default `100`, max `1000`

Station price history summary endpoint accepts:

- `stationId` path variable as UUID
- `fuelType` required
- `from` optional ISO-8601 timestamp
- `to` optional ISO-8601 timestamp
- `limit` optional, default `30`, max `365`

Example:

```text
http://localhost:8080/v1/stations/nearby?lat=51.5074&lon=-0.1278&radiusMeters=5000&fuelType=E5&limit=10
```

```text
http://localhost:8080/v1/stations/cheapest-nearby?lat=51.5074&lon=-0.1278&radiusMeters=5000&fuelType=E5&limit=10
```

```text
http://localhost:8080/v1/stations/in-bounds?bbox=-0.20,51.45,-0.05,51.55&fuelType=E5&limit=250
```

```text
http://localhost:8080/v1/stations/123e4567-e89b-12d3-a456-426614174000
```

```text
http://localhost:8080/v1/stations/123e4567-e89b-12d3-a456-426614174000/price-history?fuelType=E5&from=2026-04-18T00:00:00Z&to=2026-04-19T00:00:00Z&limit=100
```

```text
http://localhost:8080/v1/stations/123e4567-e89b-12d3-a456-426614174000/price-history/summary?fuelType=E5&from=2026-04-01T00:00:00Z&to=2026-04-30T23:59:59Z&limit=30
```

Behavior:

- `/nearby` sorts primarily by distance, then price
- `/cheapest-nearby` sorts primarily by price, then distance
- `/in-bounds` returns active station map markers inside the requested bounding box
- `/v1/stations/{stationId}` returns a single station with full address, coordinates, and all latest prices by fuel type
- `/v1/stations/{stationId}/price-history` returns historical observations from `price_observation` for one required `fuelType`
- `/v1/stations/{stationId}/price-history/summary` returns daily UTC summary buckets for one required `fuelType`
- valid queries with no matches return `200 OK` with `[]`
- a valid station detail lookup with no latest prices returns `200 OK` with `latestPrices: []`
- a valid price-history lookup with no matching observations returns `200 OK` with `observations: []`
- a valid price-history-summary lookup with no matching observations returns `200 OK` with `summaries: []`
- price history summary buckets include `bucketStart`, `bucketEnd`, `firstPricePence`, `highestPricePence`, `lowestPricePence`, `lastPricePence`, and `observationCount`
- summary buckets are grouped by calendar day in `UTC` and ordered newest bucket first
- station query endpoints are cached in-memory for repeated equivalent queries
- cache keys are based on normalized query input: trimmed/uppercased `fuelType` and resolved default `limit`
- caches are invalidated after transaction commit when the `latest_price` read model changes
- station price history and station price history summary each have their own cache and are invalidated after transaction commit when `price_observation` changes
- invalid, missing, or non-parseable parameters return HTTP `400` via a global API exception handler
- successful requests emit a single structured `info` log with path, query parameters, status, duration, and result count
- invalid requests emit a single structured `warn` log with the same request context plus a synthesized validation error message
- station detail requests for unknown UUIDs return `404 Not Found` with the standard API error payload
- station price history returns `404 Not Found` only when the station does not exist
- station price history summary returns `404 Not Found` only when the station does not exist

### OpenAPI / Swagger

The backend now exposes machine-readable OpenAPI docs plus Swagger UI:

- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

Swagger UI documents query parameters, response payloads, and standard `400` validation-style errors for the public station endpoints.

## Running Locally

### 1. Create local environment variables

Create a local `.env` file from [`.env.example`](.env.example).

Example:

```bash
cp .env.example .env
```

On Windows, create `.env` manually if needed.

### 2. Start PostgreSQL/PostGIS

```bash
docker compose up -d
```

The Docker setup reads database values from `.env`.

### 3. Provide Fuel Finder credentials

The local profile expects:

```bash
FUEL_FINDER_CLIENT_ID=your_client_id
FUEL_FINDER_CLIENT_SECRET=your_client_secret
```

These are referenced by [`backend/src/main/resources/application-local.yml`](backend/src/main/resources/application-local.yml).

### 4. Run the backend

From [`backend/`](backend):

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

On Windows PowerShell:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

### 5. Optional: run one-shot manual ingestion

If you want to trigger ingestion once on startup instead of using the scheduler:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local-manual"
```

### 6. Verify the service

Health endpoint:

```text
http://localhost:8080/actuator/health
```

Nearby stations:

```text
http://localhost:8080/v1/stations/nearby?lat=51.5074&lon=-0.1278&radiusMeters=5000&fuelType=E5&limit=10
```

Cheapest nearby stations:

```text
http://localhost:8080/v1/stations/cheapest-nearby?lat=51.5074&lon=-0.1278&radiusMeters=5000&fuelType=E5&limit=10
```

Station details:

```text
http://localhost:8080/v1/stations/123e4567-e89b-12d3-a456-426614174000
```

Station price history summary:

```text
http://localhost:8080/v1/stations/123e4567-e89b-12d3-a456-426614174000/price-history/summary?fuelType=E5&from=2026-04-01T00:00:00Z&to=2026-04-30T23:59:59Z&limit=30
```

## Configuration Notes

- Base application settings live in [`backend/src/main/resources/application.yaml`](backend/src/main/resources/application.yaml)
- Local Fuel Finder credentials are loaded from [`backend/src/main/resources/application-local.yml`](backend/src/main/resources/application-local.yml)
- Manual local ingestion settings live in [`backend/src/main/resources/application-local-manual.yml`](backend/src/main/resources/application-local-manual.yml)
- Production-specific API settings live in [`backend/src/main/resources/application-prod.yml`](backend/src/main/resources/application-prod.yml)
- Lightweight test-profile settings live in [`backend/src/test/resources/application-test.yaml`](backend/src/test/resources/application-test.yaml)
- `.env` is local-only and should never be committed

### Cache Settings

The station read API uses local in-memory caches backed by Caffeine.

Current defaults in [`backend/src/main/resources/application.yaml`](backend/src/main/resources/application.yaml):

- `fuelfinder.ingestion.reconciliation.unexplained-mismatch-action=fail`
- `fuelfinder.cache.nearby.ttl=60s`
- `fuelfinder.cache.nearby.max-size=500`
- `fuelfinder.cache.cheapest-nearby.ttl=60s`
- `fuelfinder.cache.cheapest-nearby.max-size=500`
- `fuelfinder.cache.in-bounds.ttl=60s`
- `fuelfinder.cache.in-bounds.max-size=500`
- `fuelfinder.cache.details.ttl=90s`
- `fuelfinder.cache.details.max-size=1000`
- `fuelfinder.cache.history.ttl=90s`
- `fuelfinder.cache.history.max-size=1000`
- `fuelfinder.cache.history-summary.ttl=90s`
- `fuelfinder.cache.history-summary.max-size=1000`

Notes:

- `fuelfinder.ingestion.reconciliation.unexplained-mismatch-action` accepts `fail` or `warn`; use `warn` only when you want ingestion to continue after an unexplained reconciliation mismatch while still reporting `FAILED`
- the cache is local to each application instance
- nearby, cheapest-nearby, and in-bounds cache entries are evicted automatically after `60s`
- station details, price history, and price history summary cache entries are evicted automatically after `90s`
- station detail responses are cached separately by `stationId`
- in-bounds responses are cached by normalized bounding box, normalized `fuelType`, and resolved `limit`
- station price history responses are cached by `stationId`, normalized `fuelType`, `from`, `to`, and resolved `limit`
- station price history summary responses are cached by `stationId`, normalized `fuelType`, `from`, `to`, and resolved `limit`
- detail-cache TTL should stay moderate because the payload includes latest prices as well as station metadata
- all station-query caches are cleared after a successful transaction commit that changes the `latest_price` read model
- history-cache entries are cleared after a successful transaction commit that changes `price_observation`
- the station-details cache is also cleared after a successful transaction commit that changes station metadata
- equivalent requests such as `fuelType=e5` and `fuelType=E5` reuse the same cache entry after normalization

## Testing

The backend includes unit and integration tests based on JUnit 5, Mockito, Spring Boot Test, Testcontainers, and JaCoCo coverage reporting.

Current test coverage includes:

- unit tests for OAuth token retrieval and Fuel Finder API clients
- unit tests for ingestion orchestration, station normalization, latest-price projection, price observation ingestion, utility logic, station query services, and custom exceptions
- reconciliation tests for `OK`, `OK_WITH_SKIPS`, `FAILED + FAIL`, `FAILED + WARN`, normalization skips, duplicate observations, and missing-station persistence outcomes
- cache-focused tests for normalized query keys, repeated-query cache hits, and after-commit cache invalidation behavior
- integration tests for JDBC repository writes against PostgreSQL/PostGIS
- integration tests for station details, in-bounds queries, price history, price history summaries, and cache invalidation
- integration tests for end-to-end ingestion, repeated-ingestion deduplication flows, and station field persistence

Run the full backend test suite from [`backend/`](backend):

```bash
./gradlew test
```

On Windows PowerShell:

```powershell
.\gradlew.bat test
```

Generate the JaCoCo HTML coverage report:

```bash
./gradlew test jacocoTestReport
```

On Windows PowerShell:

```powershell
.\gradlew.bat test jacocoTestReport
```

The HTML report is written to [`backend/build/reports/jacoco/test/html/index.html`](backend/build/reports/jacoco/test/html/index.html).

Run only selected unit tests:

```bash
./gradlew test --tests "uk.co.fuelfinder.api.station.StationQueryServiceTest" --tests "uk.co.fuelfinder.api.station.CachedStationQueryServiceCachingTest"
```

Run only selected integration tests:

```bash
./gradlew test --tests "uk.co.fuelfinder.ingestion.raw.writer.JdbcRepositoriesIT" --tests "uk.co.fuelfinder.ingestion.raw.orchestrator.RetailerIngestionServiceIT" --tests "uk.co.fuelfinder.ingestion.raw.orchestrator.IngestionDedupeIT"
```

Run all integration tests:

```bash
./gradlew test --tests "*IT"
```

Tests matching `*IT` run as part of the standard `test` task in this project. Integration tests require Docker because Testcontainers starts PostgreSQL/PostGIS containers automatically.

## Repository Layout

```text
fuel-finder/
|-- backend/
|   |-- build.gradle
|   |-- gradlew
|   |-- gradlew.bat
|   `-- src/
|       |-- main/
|       |   |-- java/uk/co/fuelfinder/
|       |   `-- resources/
|       `-- test/
|-- docs/
|-- docker/
|-- .env.example
|-- docker-compose.yml
`-- README.md
```

## Roadmap

Near-term priorities:

- extend read APIs with richer filters and query shapes
- extend integration tests to cover more ingestion edge cases and failure paths
- raise and enforce JaCoCo coverage thresholds over time
- align or remove unused JDBC write paths
- deepen observability beyond the current API request logging and ingestion diagnostics

## Why This Project

This project is meant to demonstrate practical backend engineering concerns such as:

- external API integration
- OAuth token management
- ingestion pipeline design
- auditability of imported data
- Postgres/PostGIS data modeling
- migration-driven schema management
- geospatial read API design on top of ingestion-driven read models

## What This Repository Demonstrates

- integration with an OAuth2-protected external API
- paginated ingestion and raw payload retention
- normalization into a relational/geospatial model
- separation between ingestion, persistence, and read APIs
- backend-first project structure designed for incremental evolution

## License

This project is licensed under the Proprietary License (All Rights Reserved). See `LICENSE` for details.
