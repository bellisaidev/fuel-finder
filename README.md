# 🚀 Fuel Finder (Backend MVP)

Geo-based fuel price aggregation platform built on the UK Fuel Finder Scheme open data.

Fuel Finder is a backend-focused MVP designed to ingest, store, and expose fuel price data through geospatial APIs, with a strong focus on data correctness, auditability, and scalable architecture.

The project simulates a **production-style backend ingestion and geospatial data platform**, rather than a simple CRUD application.

---

# 🎯 Project Goal

Build a backend system that:

* Integrates with the **UK Fuel Finder Scheme API**
* Ingests fuel station and fuel price data in batches
* Stores **full historical price data** (append-only model)
* Provides **geospatial APIs** to find the cheapest fuel nearby
* Ensures **idempotent ingestion and full auditability**
* Demonstrates **real-world backend architecture and data engineering practices**

This project is designed as:

* Portfolio project
* Backend architecture showcase
* Data ingestion platform MVP
* Potential startup MVP foundation

---

# 🧱 Tech Stack

## Backend

* Java 21
* Spring Boot 3
* Spring Web
* Spring WebClient (external API integration)
* Spring Data JPA
* Hibernate
* Hibernate Spatial
* Lombok

## Database

* PostgreSQL
* PostGIS (geospatial queries)

## Data & Migrations

* Flyway

## DevOps

* Docker
* Docker Compose

## Observability

* Spring Boot Actuator
* Structured logging

## External APIs

* UK Fuel Finder Scheme API
* OAuth2 Client Credentials flow

---

# 🧠 Architecture

**Architecture style:** Modular Monolith

Reasons:

* Simpler deployment for MVP
* Easier local development
* Clear separation of responsibilities
* Can be split into microservices later if needed

### High-level modules

```text
ingestion/
  auth/        -> OAuth2 token retrieval & caching
  fetch/       -> External API clients (Fuel Finder)
  normalize/   -> Data normalization (external → domain)
  service/     -> Ingestion orchestration

domain/
  station/
  price/
  retailer/

api/
  station/
  price/

config/
```

### Architectural principles

* Clear separation between ingestion, domain, and API layers
* Idempotent ingestion pipeline
* Append-only historical data
* Raw data audit trail
* External API treated as unreliable source
* Read-optimized models for API
* Avoid premature microservices
* Design for scalability from day one

---

# 🔐 External API Integration (Fuel Finder)

The UK Fuel Finder API is accessed using **OAuth2 Client Credentials flow**.

### Integration flow

1. Request access token using `client_id` and `client_secret`
2. Cache token in memory until expiration
3. Use Bearer token to call Fuel Finder endpoints
4. Fetch data in batches
5. Store raw payload for audit
6. Normalize and persist data

### OAuth components

| Component                    | Responsibility                       |
| ---------------------------- | ------------------------------------ |
| `OAuthTokenClient`           | Calls `/oauth/generate_access_token` |
| `FuelFinderTokenProvider`    | Token caching & refresh              |
| `FuelFinderWebClientConfig`  | WebClient configuration              |
| `FuelFinderApiProperties`    | External API configuration           |
| `FuelFinderPfsClient`        | Fetch stations                       |
| `FuelFinderFuelPricesClient` | Fetch fuel prices                    |

This design simulates **production-grade external API integration** with token caching and error handling.

---

# 📊 Data Model

Core tables:

| Table               | Purpose                        |
| ------------------- | ------------------------------ |
| `retailer`          | Data source                    |
| `raw_feed_fetch`    | Raw JSON + audit trail         |
| `station`           | Fuel station with geo location |
| `price_observation` | Append-only price history      |
| `latest_price`      | Read model for fast queries    |

### Key design decisions

* **Append-only** price model (no updates, only inserts)
* `observed_at` = ingestion time (source of truth)
* Feed timestamps are not trusted
* DB-level deduplication via `source_hash`
* PostGIS `geography(Point,4326)` for geo queries
* Full auditability of external data
* Separation between **write model** and **read model**

This approach is commonly used in **data platforms and event-style systems**.

---

# 🔄 Ingestion Pipeline

End-to-end ingestion flow:

```text
OAuth Token
    ↓
Fetch Stations (PFS)
    ↓
Fetch Fuel Prices
    ↓
Store RAW JSON (raw_feed_fetch)
    ↓
Normalize data
    ↓
Upsert station
    ↓
Insert price_observation (append-only)
    ↓
Update latest_price (read model)
    ↓
Expose Geo API
```

### Pipeline guarantees

* Idempotent ingestion
* Full audit trail
* No duplicate price entries
* Historical data preserved
* Safe reprocessing of feeds
* Source traceability
* Replay capability from raw data

---

# 🌍 API (Planned)

## Nearby stations

```http
GET /v1/stations/nearby?lat=&lon=&radiusMeters=&fuelType=&sort=price|distance
```

Returns:

* station id
* brand
* address
* postcode
* lat/lon
* distance_meters
* fuel_type
* price_pence
* observed_at

Uses PostGIS:

* `ST_DWithin`
* `ST_Distance`

---

## Station detail

```http
GET /v1/stations/{id}
```

---

## Price history

```http
GET /v1/stations/{id}/prices?fuelType=&from=&to=&page=
```

---

# 🗺️ Roadmap

## ✅ Completed

* Project structure initialized
* Docker Compose (Postgres + PostGIS)
* Spring Boot backend running
* Flyway migrations
* Database schema created
* JPA entities & repositories
* Hibernate Spatial configured
* Fuel Finder OAuth2 integration
* Token caching mechanism
* External API configuration
* WebClient integration
* Structured logging for ingestion
* Fetch stations (PFS)
* Fetch fuel prices

---

## 🚧 In Progress — Ingestion Pipeline

* Store raw payload (`raw_feed_fetch`)
* Normalize stations and prices
* Upsert stations
* Insert deduplicated price observations
* Update latest_price read model

---

## ⏭️ Next Steps

### Step 6 — Raw Data Storage

* Store raw API responses
* Source hashing
* Audit trail

### Step 7 — Scheduler

* Periodic ingestion
* DB locking (ShedLock)

### Step 8 — Geo API

* `/v1/stations/nearby`
* PostGIS queries

### Step 9 — Station APIs

* Station detail
* Price history

### Step 10 — Testing

* Parsing tests
* Deduplication tests
* Integration tests
* Testcontainers

### Step 11 — Documentation

* ADRs (Architecture Decision Records)
* API documentation
* Data model documentation

### Step 12 — Cloud Deployment

* AWS deployment
* Terraform
* Monitoring (Grafana)
* CI/CD

---

# ⚙️ Running Locally

Create a local `.env` file from `.env.example` before starting Docker. Keep `.env` untracked and use only placeholder values in committed files.

Start database:

```bash
docker compose up -d
```

Run backend:

```bash
cd backend
./gradlew bootRun
```

Health check:

```bash
http://localhost:8080/actuator/health
```

---

# 🔑 Environment Variables

Fuel Finder API requires OAuth credentials:

```bash
FUEL_FINDER_CLIENT_ID=your_client_id
FUEL_FINDER_CLIENT_SECRET=your_client_secret
```

Loaded via:

```text
application-local.yml
application-prod.yml
```

Local Docker database settings are read from `.env`. Commit `.env.example`, never `.env`.

---

# 💡 Why This Project Matters

This project demonstrates:

* External API integration with OAuth2
* Token caching strategies
* Resilient API client design
* Data ingestion pipelines
* Idempotent processing
* Append-only data modeling
* Geospatial queries with PostGIS
* Read model vs write model separation
* Structured logging and observability
* Docker-based infrastructure
* Database migrations with Flyway
* Clean backend architecture

This is **not a tutorial CRUD project** — it is designed to simulate a **production-ready backend ingestion and geospatial data platform**.

---

# 👤 Author

**Fabrizio Bellisai**
Backend Java Engineer
Java • Spring Boot • PostgreSQL • PostGIS • Docker • AWS

---

# 📄 License

MVP project for educational, portfolio, and experimental purposes.

---
