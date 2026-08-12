# Fuel Finder

Fuel Finder is a backend Java/Spring Boot project for ingesting and storing data from the UK Fuel Finder Scheme.

The repository covers the ingestion pipeline and a geospatial read API, with production-oriented HTTP resilience and metrics: OAuth authentication, paginated feed retrieval, raw payload storage, station normalization, PostgreSQL/PostGIS persistence, public station lookup endpoints, bounded/retried upstream HTTP access, and Prometheus/Grafana observability.

## Current Status

What is implemented today:

- Spring Boot backend with Java 21
- PostgreSQL + PostGIS local environment via Docker Compose
- Optional Prometheus and Grafana local observability stack
- Backend Docker image build
- GitHub Actions CI for tests, coverage verification, JAR build, container scanning, and verified GHCR publishing
- Flyway database migrations
- OAuth2 client credentials integration with the Fuel Finder API
- Shared bounded Reactor Netty transport with configurable connection pooling, timeouts, retry/backoff, `Retry-After` handling, and centralized failure mapping
- Paginated retrieval of PFS and fuel price feeds
- Raw feed persistence for auditability
- Station normalization and upsert flow
- Ingestion reconciliation checks for raw, normalized, skipped, duplicate, and persisted counts
- Fuel price normalization, deduplicated observation ingestion, and latest price projection/backfill
- Geospatial read APIs for nearby stations, cheapest nearby stations, map bounds, station details, and price history
- Local Caffeine caching for repeated geospatial read queries with transaction-safe invalidation after latest-price updates
- Global API error handling for invalid query parameters
- Lightweight operational logging for public station queries with success timing/result counts and consistent `400` warnings
- Spring Boot Actuator and Micrometer metrics for HTTP, JVM, process, datasource/HikariCP, managed WebClient, and Caffeine caches
- Low-cardinality ingestion outcome, duration, reconciliation, processed-count, and freshness metrics
- Prometheus scrape export with a provisioned local Grafana datasource and Fuel Finder dashboard
- Prometheus-managed operational alert rules for target reachability, ingestion health, reconciliation failures, and database pool saturation
- Station persistence enriched with `address`, `city`, `county`, `country`, and `postcode`
- Persistence model and schema for retailers, raw feeds, stations, price observations, and latest prices
- Unit tests with JUnit 5 and Mockito across auth, client, normalization, exception, and ingestion orchestration components
- Integration tests with Spring Boot Test and Testcontainers for repository persistence, end-to-end ingestion, deduplication, cache invalidation, and station read flows

What is still in progress:

- Additional read filters and query shapes beyond the current station endpoints
- Broader integration coverage across more failure scenarios and ingestion edge cases

## Tech Stack

- Java 21
- Spring Boot 3
- Spring Web
- Spring WebFlux `WebClient`
- Spring Data JPA
- Spring Cache
- Spring Boot Actuator
- Micrometer
- Hibernate Spatial
- PostgreSQL
- PostGIS
- Flyway
- Caffeine
- Prometheus
- Grafana
- Docker
- GitHub Actions
- Lombok
- JUnit 5
- Mockito
- Testcontainers
- JaCoCo

## Architecture

The codebase is structured as a modular monolith with a backend-first focus.

Main areas:

- `config/`: Spring configuration, WebClient setup, and cache configuration/properties
- `ingestion/raw/auth/`: Fuel Finder API properties, OAuth clients, token management
- `ingestion/raw/client/`: external feed clients and DTOs
- `ingestion/raw/http/`: retry policy, `Retry-After` handling, and HTTP failure classification
- `ingestion/raw/orchestrator/`: ingestion coordination
- `ingestion/raw/writer/`: raw payload storage
- `ingestion/normalize/`: station normalization, price normalization, observation ingestion, and latest price projection
- `api/station/`: station read endpoints for nearby, cheapest-nearby, map bounds, details, and price history lookups
- `persistence/entity/`: JPA entities
- `persistence/repository/`: Spring Data repositories
- `observability/`: low-cardinality ingestion meters and their clock configuration

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

### Fuel Finder HTTP resilience

The OAuth and authenticated Fuel Finder clients share one bounded Reactor Netty connection pool and use explicit connection and response timeouts. Transient connection failures, timeouts, HTTP 408/429, and HTTP 500/502/503/504 responses are retried up to two times with exponential backoff and jitter. Valid `Retry-After` values on 429 and 503 responses are honored as minimum delays up to a separate 30-second operational limit. Exhausted connectivity failures, authentication failures, other HTTP failures, and unexpected integration failures are mapped into distinct application exceptions.

The current values are initial operational defaults and are externally configurable under `fuelfinder.api.http`. With three attempts, a 20-second response timeout, and two permitted 30-second `Retry-After` delays, one logical request can approach roughly two minutes. This budget, along with pool sizing and retry limits, should be revisited using ingestion-duration metrics and representative load testing.

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

Prometheus scrape endpoint:

```text
http://localhost:8080/actuator/prometheus
```

### 7. Optional: start Prometheus and Grafana

The default Compose workflow remains database-only. With the backend running on the host, start the optional observability services with:

```bash
docker compose --profile observability up -d
```

Local endpoints:

- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`
- Grafana dashboard: **Fuel Finder / Fuel Finder Overview**

Grafana uses `admin` / `admin` for local development unless `GRAFANA_ADMIN_USER` and `GRAFANA_ADMIN_PASSWORD` are set. Prometheus scrapes the host-run backend at `host.docker.internal:8080`.

Stop only the optional monitoring services, leaving PostgreSQL running:

```bash
docker compose --profile observability stop prometheus grafana
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

## Docker

Build the backend image from the repository root:

```bash
docker build -f backend/Dockerfile backend -t fuel-finder-backend:local
```

Or from [`backend/`](backend):

```bash
docker build -t fuel-finder-backend:local .
```

The Docker image does not hard-code a Spring profile. Production deployments should explicitly set `SPRING_PROFILES_ACTIVE=prod` at runtime and provide the required database and Fuel Finder API configuration through environment variables/secrets.

Trusted pushes to `master` publish the verified runtime image to GitHub Container Registry using only the full Git commit SHA as its tag:

```text
ghcr.io/bellisaidev/fuel-finder:<full-git-sha>
```

The SHA tag is write-once in the CI workflow. A rerun validates and scans the existing registry artifact by digest without rebuilding or overwriting it. The workflow reports the resolved reference in its job summary:

```text
ghcr.io/bellisaidev/fuel-finder@sha256:<verified-registry-digest>
```

Future staging configuration should consume the digest-qualified reference so that it runs the exact verified manifest. This workflow does not deploy the image. After the first image is published privately, its SHA tag, OCI revision label, and digest must be verified before a maintainer deliberately changes the GHCR package visibility to public. Once public, either reference can be pulled anonymously:

```bash
docker pull ghcr.io/bellisaidev/fuel-finder:<full-git-sha>
docker pull ghcr.io/bellisaidev/fuel-finder@sha256:<verified-registry-digest>
```

## Production Runtime Configuration

The Docker image is environment-agnostic: it does not set a Spring profile, so the same image can be promoted between environments. A production deployment must set `SPRING_PROFILES_ACTIVE=prod`. This is a deployment requirement rather than an `application-prod.yml` placeholder.

The `prod` profile requires the following runtime configuration. The placeholders have no usable defaults.

| Variable | Description |
|---|---|
| `FUEL_FINDER_CLIENT_ID` | OAuth2 client ID for the external Fuel Finder API. Must not be blank. |
| `FUEL_FINDER_CLIENT_SECRET` | OAuth2 client secret for the external Fuel Finder API. Must not be blank. |
| `SPRING_DATASOURCE_URL` | JDBC URL for the production PostgreSQL/PostGIS database. |
| `SPRING_DATASOURCE_USERNAME` | Production database username. |
| `SPRING_DATASOURCE_PASSWORD` | Production database password. |

Missing required placeholders prevent production configuration from being resolved. Missing or blank OAuth credentials also fail configuration binding through Bean Validation, before ingestion or an external API client can start. Database settings are supplied through Spring Boot's standard datasource environment variables; no custom `DB_*` aliases are used.

Secrets must be supplied by the deployment environment or its secret manager and must never be committed. Copy [`.env.prod.example`](.env.prod.example) to the ignored `.env.prod` file only for a local container exercise, then replace every placeholder:

```bash
cp .env.prod.example .env.prod
```

An equivalent container invocation is:

```bash
docker run --rm -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e FUEL_FINDER_CLIENT_ID='<client-id>' \
  -e FUEL_FINDER_CLIENT_SECRET='<client-secret>' \
  -e SPRING_DATASOURCE_URL='jdbc:postgresql://<database-host>:5432/<database-name>' \
  -e SPRING_DATASOURCE_USERNAME='<database-user>' \
  -e SPRING_DATASOURCE_PASSWORD='<database-password>' \
  fuel-finder-backend:local
```

### Effective production defaults

These values are explicitly defined by the current base and production YAML configuration:

| Setting | Effective value in `prod` |
|---|---|
| Server port | `8080` |
| Fuel Finder API base URL | `https://www.fuel-finder.service.gov.uk/api/v1` |
| OAuth token path | `/oauth/generate_access_token` |
| OAuth refresh path | `/oauth/regenerate_access_token` |
| Ingestion scheduler | Enabled, every 30 minutes |
| Ingestion retailer | `FUEL_FINDER_API` |
| Actuator web exposure | `health,info,prometheus` |
| Health details | `never` |
| OpenAPI API docs | Disabled |
| Swagger UI | Disabled |

### Optional HTTP resilience overrides

The production values under `fuelfinder.api.http` are operational defaults, not mandatory environment variables. Spring Boot relaxed binding allows individual settings to be overridden when deployment conditions require tuning:

| Property | Environment variable | Default |
|---|---|---|
| `fuelfinder.api.http.connect-timeout` | `FUELFINDER_API_HTTP_CONNECT_TIMEOUT` | `5s` |
| `fuelfinder.api.http.response-timeout` | `FUELFINDER_API_HTTP_RESPONSE_TIMEOUT` | `20s` |
| `fuelfinder.api.http.pool.max-connections` | `FUELFINDER_API_HTTP_POOL_MAX_CONNECTIONS` | `20` |
| `fuelfinder.api.http.pool.pending-acquire-max-count` | `FUELFINDER_API_HTTP_POOL_PENDING_ACQUIRE_MAX_COUNT` | `40` |
| `fuelfinder.api.http.pool.pending-acquire-timeout` | `FUELFINDER_API_HTTP_POOL_PENDING_ACQUIRE_TIMEOUT` | `5s` |
| `fuelfinder.api.http.pool.max-idle-time` | `FUELFINDER_API_HTTP_POOL_MAX_IDLE_TIME` | `30s` |
| `fuelfinder.api.http.pool.max-life-time` | `FUELFINDER_API_HTTP_POOL_MAX_LIFE_TIME` | `5m` |
| `fuelfinder.api.http.pool.eviction-interval` | `FUELFINDER_API_HTTP_POOL_EVICTION_INTERVAL` | `30s` |
| `fuelfinder.api.http.retry.max-retries` | `FUELFINDER_API_HTTP_RETRY_MAX_RETRIES` | `2` |
| `fuelfinder.api.http.retry.initial-backoff` | `FUELFINDER_API_HTTP_RETRY_INITIAL_BACKOFF` | `500ms` |
| `fuelfinder.api.http.retry.max-backoff` | `FUELFINDER_API_HTTP_RETRY_MAX_BACKOFF` | `5s` |
| `fuelfinder.api.http.retry.jitter` | `FUELFINDER_API_HTTP_RETRY_JITTER` | `0.5` |
| `fuelfinder.api.http.retry.max-retry-after` | `FUELFINDER_API_HTTP_RETRY_MAX_RETRY_AFTER` | `30s` |

Only override these settings with an explicit operational reason and validate the resulting worst-case request and ingestion duration.

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
- Caffeine statistics are enabled and exported through Spring Boot's built-in `cache.*` metrics

### Metrics and Local Observability

Adding the Prometheus Micrometer registry makes metrics available at:

```text
/actuator/prometheus
```

This endpoint must be reachable by Prometheus or equivalent monitoring infrastructure, but it must not be exposed as a public application endpoint. Restrict it through deployment networking, firewall rules, a private management route, or reverse-proxy access policy.

Fuel Finder custom meters use bounded tags only:

| Java meter | Type | Tags | Meaning |
|---|---|---|---|
| `fuelfinder.ingestion.duration` | Timer | `outcome=success\|failure` | Complete ingestion execution duration; the Timer count is also the run count |
| `fuelfinder.ingestion.reconciliation` | Counter | `status=ok\|ok_with_skips\|failed` | Reconciliation outcomes |
| `fuelfinder.ingestion.stations.processed` | Counter | none | Accepted and normalized station records |
| `fuelfinder.ingestion.prices.processed` | Counter | none | Accepted and normalized price observations |
| `fuelfinder.ingestion.last.attempt.timestamp` | Gauge (epoch seconds) | none | Last attempt start |
| `fuelfinder.ingestion.last.success.timestamp` | Gauge (epoch seconds) | none | Last successful completion |

#### Verified Prometheus metric contract

The alert rules and dashboard rely on the following verified exported series:

| Exported metric | Type | Bounded application labels | Meaning |
|---|---|---|---|
| `fuelfinder_ingestion_duration_seconds_count` | Timer count/counter | `outcome=success\|failure` | Completed ingestion executions by outcome |
| `fuelfinder_ingestion_reconciliation_total` | Counter | `status=ok\|ok_with_skips\|failed` | Reconciliation outcomes |
| `fuelfinder_ingestion_last_attempt_timestamp_seconds` | Gauge | none | Epoch timestamp when the latest ingestion attempt started |
| `fuelfinder_ingestion_last_success_timestamp_seconds` | Gauge | none | Epoch timestamp when the latest successful ingestion completed |
| `process_uptime_seconds` | Gauge | none | Current application process uptime |
| `hikaricp_connections_active` | Gauge | `pool` | Active database connections by configured HikariCP pool |
| `hikaricp_connections_max` | Gauge | `pool` | Maximum database connections by configured HikariCP pool |
| `hikaricp_connections_pending` | Gauge | `pool` | Threads waiting to borrow a database connection |
| `http_server_requests_seconds_count` | Timer count/counter | `status` and `uri` among the available labels | Completed Spring MVC requests |
| `http_server_requests_seconds_sum` | Timer sum | `status` and `uri` among the available labels | Total duration of completed Spring MVC requests |

The `outcome` and reconciliation `status` values above are the complete application-defined sets. The `pool` label is bounded by the configured datasource pools. Spring MVC supplies labels including HTTP `status` and templated `uri`; alert and dashboard queries avoid raw request paths. Prometheus adds the target labels `job` and `instance` when it scrapes these series.

Additional custom metrics, including processed station and price counters and the Timer sum/max series, remain available at the scrape endpoint. The table above is the stable contract used by the current operational alert rules and the relevant dashboard panels.

#### Freshness and restart semantics

Both ingestion timestamp gauges reset to `0` whenever the application process restarts. Zero means **not observed since process start**; it must not be interpreted as a Unix epoch observation. `process_uptime_seconds` provides the startup grace used by the missing-attempt alert. The provisioned dashboard filters zero timestamps before calculating freshness and displays “Not observed since process start”.

Counters, including ingestion Timer counts and reconciliation totals, also reset when the application restarts. Prometheus `increase()` queries handle ordinary counter resets, but an alert window that crosses a restart may contain less evidence than a window from a continuously running process.

The Prometheus endpoint also exports Spring Boot/Micrometer metrics instead of duplicating them with custom Fuel Finder meters:

- `http.server.requests` for API request rate, error rate, and average latency
- `jvm.*` for JVM memory
- `process.*` for process CPU, start time, and uptime
- `jdbc.connections.*` and `hikaricp.*` for the datasource pool
- `http.client.requests` for managed Fuel Finder WebClient requests
- `cache.*` for Caffeine cache statistics

The currently provisioned **Fuel Finder Overview** dashboard visualizes:

- ingestion completions split by outcome, average duration, and recent/time-window maximum duration
- reconciliation outcomes and time since the last successful ingestion
- Spring MVC API request rate, 4xx/5xx error rate, and average latency
- JVM heap usage
- HikariCP active, idle, pending, and maximum connections

Processed station/price counters, process metrics, generic JDBC pool metrics, managed WebClient metrics, and Caffeine metrics are available through Prometheus but are not currently shown on the dashboard.

The dashboard panel titled **Recent/time-window maximum ingestion duration** uses Micrometer Timer max. This is a decaying time-window maximum, not an all-time maximum.

#### Alert ownership and notification status

Prometheus evaluates every active rule in [`observability/prometheus/rules/fuel-finder-alerts.yml`](observability/prometheus/rules/fuel-finder-alerts.yml). Grafana remains the visualization layer and has no Grafana-managed alert rules.

Alertmanager is not configured. Consequently, no email, Slack, webhook, or other notification delivery exists. The rule labels `action=page` and `action=warn` are future routing classifications only; they do not send notifications. Pending and firing alerts are currently visible through the Prometheus UI and API.

#### Active alert catalogue

| Alert | Purpose | Severity | Action | Threshold/window | Pending duration | Main operational limitation |
|---|---|---|---|---|---|---|
| `FuelFinderTargetUnavailable` | Detect that Prometheus cannot scrape the Fuel Finder target. | `critical` | `page` | `up{job="fuel-finder"} == 0` | `2m` | `up` proves scrape reachability only, not external Fuel Finder API or application end-to-end availability. |
| `FuelFinderIngestionAttemptMissing` | Detect a missing or stale ingestion attempt while the target remains scrapeable. | `critical` | `page` | Last-attempt timestamp is older than `45m`, or remains zero after process uptime exceeds `45m`; requires `up == 1`. | `5m` | The timestamp is also updated by manual ingestion and proves neither scheduler execution nor ShedLock acquisition; a long-running ingestion can make the next attempt appear overdue. |
| `FuelFinderIngestionNotSucceeding` | Detect repeated completed ingestion failures without an intervening success. | `critical` | `page` | At least `2` failures and `0` successes in `75m`, with a non-zero attempt no older than `45m` and `up == 1`. | `5m` | Completion counters reset on restart, and manual and scheduled runs are indistinguishable. |
| `FuelFinderReconciliationFailed` | Surface unexplained reconciliation mismatches. | `warning` | `warn` | More than `0` `status="failed"` outcomes in `45m`. | None; active on the first matching evaluation. | The metric exposes status but not mismatch reason or magnitude. |
| `FuelFinderDatabasePoolSaturated` | Detect sustained HikariCP connection pressure with waiting borrowers. | `warning` | `warn` | Active/max connections at least `90%` and pending borrowers greater than `0`, matched by `job`, `instance`, and `pool`. | `5m` | It identifies pool contention but not whether the cause is database health, slow queries, leaks, or application load. |

The `action` column records intended future delivery treatment only. It has no effect until notification routing is implemented.

#### Reconciliation alert policy

A reconciliation outcome of `failed` is an active warning because it represents an unexplained accounting mismatch. `ok_with_skips` remains dashboard-only: it combines normal duplicate handling with other accounted skips and exposes neither the reason nor magnitude needed for a reliable alert threshold.

#### Dashboard-only HTTP candidates

The 4xx ratio remains visible as an operational and client-validation signal; it is intentionally separate from server failures. The dashboard also shows two informational candidates over non-Actuator traffic:

- 5xx ratio above `5%` with at least `20` requests in `10m`
- mean latency above `1s` with at least `20` requests in `10m`

Neither condition is an active Prometheus alert, and Grafana does not evaluate them as alerts. Production traffic baselines and an explicit API service objective are required before either candidate can be promoted to an alert rule.

#### Known alerting limitations

- Manual ingestion updates the same attempt, completion, and freshness metrics as scheduled ingestion.
- The last-attempt timestamp does not prove that the scheduler executed or that ShedLock was acquired.
- ShedLock has no dedicated metric.
- Because the last-attempt gauge records the attempt start, a long-running ingestion may resemble a missing subsequent attempt.
- Prometheus `up` measures scrape reachability, not external end-to-end Fuel Finder API availability.
- Prometheus cannot detect or report its own failure without an external monitor.
- Application restarts reset timestamp gauges and counters as described above.

#### Local validation and inspection

The observability files are under [`observability/`](observability). The profile is intentionally optional. Run these commands from the repository root:

```bash
# Database only
docker compose up -d

# Database, Prometheus, and Grafana
docker compose --profile observability up -d

# Stop Prometheus and Grafana without stopping PostgreSQL
docker compose --profile observability stop prometheus grafana
```

With the host-run backend available on port `8080`, inspect:

- [Prometheus Rules UI](http://localhost:9090/rules)
- [Prometheus Rules API](http://localhost:9090/api/v1/rules)
- [Prometheus Alerts API](http://localhost:9090/api/v1/alerts)

For command-line inspection:

```bash
curl --fail --silent http://localhost:9090/api/v1/rules
curl --fail --silent http://localhost:9090/api/v1/alerts
```

The stop command names only the current Compose services `prometheus` and `grafana`, so the `db` service and its PostgreSQL data remain running.

## CI

GitHub Actions CI is defined in [ci.yml](.github/workflows/ci.yml).

The repository's blocking and informational security checks are described in the [CI security policy](docs/ci-security-policy.md).

The workflow runs on push and pull requests targeting `master`, using the backend module as its working directory. It currently:

- sets up Java 21 with Temurin
- sets up Gradle caching
- runs `./gradlew test`
- runs `./gradlew jacocoTestCoverageVerification`
- builds the Spring Boot JAR with `./gradlew bootJar`
- builds and scans a local backend image without authenticating or publishing on pull requests
- reconciles the full-SHA GHCR tag before building on trusted pushes to `master`
- builds, scans, and publishes a missing SHA image once, or scans and reuses the existing canonical digest
- reports High/Critical vulnerabilities while blocking publication or reuse only for Critical findings
- reports the verified registry digest for future staging consumption

The test task includes integration tests matching `*IT`, so CI requires Docker for Testcontainers.

High findings remain informational. In the publishing job, SARIF upload failures are also non-blocking. Docker build failures, Trivy execution failures, and Critical findings block publication or reuse. Separate security workflows review pull-request dependency changes and analyze Java with CodeQL.

## Testing

The backend includes unit and integration tests based on JUnit 5, Mockito, Spring Boot Test, Testcontainers, and JaCoCo coverage reporting.

Current test coverage includes:

- unit tests for OAuth token retrieval and Fuel Finder API clients
- HTTP resilience tests for retryable and non-retryable failures, exponential backoff/jitter, `Retry-After`, timeout/pool property validation, failure mapping, and Spring component wiring
- unit tests for ingestion orchestration, station normalization, latest-price projection, price observation ingestion, utility logic, station query services, and custom exceptions
- unit tests for the instrumented ingestion execution boundary and custom ingestion metrics, including bounded tags, durations, processed counts, outcomes, and timestamps
- reconciliation tests for `OK`, `OK_WITH_SKIPS`, `FAILED + FAIL`, `FAILED + WARN`, normalization skips, duplicate observations, and missing-station persistence outcomes
- cache-focused tests for normalized query keys, repeated-query cache hits, and after-commit cache invalidation behavior
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

From a clean checkout, run the tests before verifying the configured JaCoCo coverage threshold:

```bash
./gradlew test
./gradlew jacocoTestCoverageVerification
```

On Windows PowerShell:

```powershell
.\gradlew.bat test
.\gradlew.bat jacocoTestCoverageVerification
```

Build the Spring Boot JAR:

```bash
./gradlew bootJar
```

On Windows PowerShell:

```powershell
.\gradlew.bat bootJar
```

Generate the JaCoCo HTML coverage report:

```bash
./gradlew test jacocoTestReport
```

On Windows PowerShell:

```powershell
.\gradlew.bat test jacocoTestReport
```

The HTML report is written to `backend/build/reports/jacoco/test/html/index.html`.

Run only selected unit tests:

```bash
./gradlew test --tests "uk.co.fuelfinder.api.station.StationQueryServiceTest" --tests "uk.co.fuelfinder.api.station.CachedStationQueryServiceCachingTest"
```

Run only selected integration tests:

```bash
./gradlew test --tests "uk.co.fuelfinder.ingestion.raw.orchestrator.RetailerIngestionServiceIT" --tests "uk.co.fuelfinder.ingestion.raw.orchestrator.IngestionDedupeIT"
```

Run tests that follow the `*IT` naming convention:

```bash
./gradlew test --tests "*IT"
```

This selector does not include integration-style classes whose names end in `IntegrationTest`. Use the regular `./gradlew test` task for the complete test suite. Tests matching `*IT` also run as part of that standard task. Testcontainers-based tests require Docker because they start PostgreSQL/PostGIS containers automatically.

## Repository Layout

```text
fuel-finder/
|-- .github/
|   `-- workflows/
|       |-- ci.yml
|       |-- codeql.yml
|       `-- dependency-review.yml
|-- backend/
|   |-- .dockerignore
|   |-- .gitattributes
|   |-- .gitignore
|   |-- Dockerfile
|   |-- HELP.md
|   |-- build.gradle
|   |-- gradle.properties
|   |-- gradle/
|   |   `-- wrapper/
|   |-- gradlew
|   |-- gradlew.bat
|   |-- settings.gradle
|   `-- src/
|       |-- main/
|       |   |-- java/uk/co/fuelfinder/
|       |   |   |-- api/
|       |   |   |-- common/
|       |   |   |-- config/
|       |   |   |-- ingestion/
|       |   |   |   |-- exception/
|       |   |   |   |-- normalize/
|       |   |   |   `-- raw/
|       |   |   |       |-- auth/
|       |   |   |       |-- client/
|       |   |   |       |-- http/
|       |   |   |       |-- orchestrator/
|       |   |   |       `-- writer/
|       |   |   |-- observability/
|       |   |   `-- persistence/
|       |   |       |-- entity/
|       |   |       `-- repository/
|       |   `-- resources/
|       |       |-- db/migration/
|       |       |-- application.yaml
|       |       |-- application-local.yml
|       |       |-- application-local-manual.yml
|       |       |-- application-prod.yml
|       |       `-- templates/
|       `-- test/
|           |-- java/
|           `-- resources/
|-- observability/
|   |-- prometheus/
|   |   `-- prometheus.yml
|   `-- grafana/
|       |-- dashboards/
|       `-- provisioning/
|-- docs/
|   `-- ci-security-policy.md
|-- .env.example
|-- .env.prod.example
|-- .gitignore
|-- docker-compose.yml
|-- LICENSE
`-- README.md
```

## Roadmap

Near-term priorities:

- extend read APIs with richer filters and query shapes
- extend integration tests to cover more ingestion edge cases and failure paths
- tune the existing HTTP timeout, pool, and retry defaults with representative load and ingestion-duration data
- configure Alertmanager notification delivery and route the existing `page` and `warn` classifications
- add external monitoring for Prometheus and end-to-end service availability
- tune alert thresholds against production traffic and ingestion behavior
- define an API service objective and promote the HTTP 5xx/latency candidates only when production evidence supports it
- secure and route the Prometheus endpoint within the eventual production monitoring network
- evaluate distributed tracing only if cross-service diagnostic needs justify it
- define semantic release tags and add deployment/promotion workflows
- raise JaCoCo coverage thresholds over time

Bounded HTTP resilience, metrics/dashboard observability, and Prometheus alert-rule evaluation are implemented foundations; the remaining observability work is notification delivery, external monitoring, production threshold tuning, HTTP alert promotion, and controlled production access.

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
- bounded Reactor Netty connection management with validated timeouts, retry/backoff, `Retry-After`, and failure classification
- paginated ingestion and raw payload retention
- normalization into a relational/geospatial model
- count-based ingestion reconciliation with explicit skip and failure outcomes
- deduplicated historical observations and a latest-price read model
- PostGIS-backed station queries with local Caffeine caching and transaction-safe invalidation
- an explicit production Actuator endpoint-exposure policy and low-cardinality Micrometer metrics
- an optional, reproducible Prometheus/Grafana local monitoring stack with a provisioned dashboard
- unit, Spring context, and Testcontainers integration testing with JaCoCo verification
- separation between ingestion, persistence, and read APIs
- backend-first project structure designed for incremental evolution

## License

This project is licensed under the Proprietary License (All Rights Reserved). See `LICENSE` for details.
