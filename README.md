
# 🚀 Fuel Finder (MVP)

Geo-based fuel price aggregator built on UK Fuel Finder Scheme open data.

Fuel Finder is a backend-focused MVP designed to aggregate fuel price data from multiple retailers and expose geospatial APIs to find the cheapest fuel nearby.

The project focuses on data correctness, auditability, and scalable design, rather than UI polish.

---

# 🎯 Project Goal

Build a public MVP (potential startup) that:

* Aggregates fuel price data from UK Fuel Finder Scheme
* Stores full historical price data per station
* Provides geo-based APIs for “cheapest fuel near me”
* Is designed to scale without premature overengineering
* Demonstrates real-world backend architecture and data engineering practices

---

# 🧱 Tech Stack

## Backend

* Java 21
* Spring Boot 3
* Spring Web
* Spring WebClient (for external APIs)
* Spring Data JPA
* Hibernate + Hibernate Spatial
* Lombok

## Database

* PostgreSQL
* PostGIS

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

* Modular Monolith
* No microservices (MVP phase)
* No Kafka (MVP phase)
* Clear separation between ingestion, domain, and API layers

### Package structure (high-level)

```id="xwe5g3"
ingestion/
  auth/        -> OAuth2 token retrieval & caching
  fetch/       -> External API clients (Fuel Finder)
  normalize/   -> Data normalization
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

Principles:

* Keep it simple
* Focus on correctness
* Idempotent ingestion
* Append-only historical data
* Avoid premature complexity
* Design for scalability from day one

---

# 🔐 External API Integration (Fuel Finder)

The UK Fuel Finder API is accessed using **OAuth2 Client Credentials flow**.

Flow:

1. Request access token using client_id and client_secret
2. Cache token in memory until expiration
3. Use Bearer token to call Fuel Finder endpoints
4. Fetch data in batches
5. Store raw payload for audit
6. Normalize and persist data

OAuth components:

* `OAuthTokenClient` → Calls `/oauth/generate_access_token`
* `FuelFinderTokenProvider` → Token caching & refresh
* `FuelFinderWebClientConfig` → WebClient configuration
* `FuelFinderApiProperties` → External API configuration

This design avoids requesting a token on every API call and simulates production-grade API integration patterns.

---

# 📊 Data Model

Core entities:

* **retailer** → data source
* **raw_feed_fetch** → raw JSON + audit trail
* **station** → fuel station with geo location
* **price_observation** → append-only price history
* **latest_price** → read model for fast queries

Key design decisions:

* Append-only price model
* `observed_at` = ingestion time (source of truth)
* Feed timestamps are not trusted
* DB-level deduplication via `source_hash`
* PostGIS `geography(Point,4326)` for geo queries
* Full auditability of external data

---

# 🔄 Ingestion Pipeline

End-to-end ingestion flow:

1. Fetch data from Fuel Finder API
2. Store raw payload (`raw_feed_fetch`)
3. Parse & normalize data
4. Upsert stations
5. Insert price observations (idempotent)
6. Update `latest_price`

Guarantees:

* Idempotent ingestion
* Full audit trail
* No duplicate price entries
* Historical data preserved
* Safe reprocessing of feeds
* Source traceability

---

# 🌍 API (Planned)

## Nearby stations

```id="6z4w1p"
GET /v1/stations/nearby?lat=&lon=&radiusMeters=&fuelType=&sort=price|distance
```

Returns:

* station id, brand, address, postcode
* lat/lon
* distance_meters
* fuel_type
* price_pence
* observed_at

---

## Station detail

```id="n9w7ho"
GET /v1/stations/{id}
```

---

## Price history

```id="i0p7ks"
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

---

## 🚧 In Progress

Step 5 — Ingestion pipeline

* OAuth token retrieval
* Call Fuel Finder API
* Fetch fuel stations (PFS)
* Fetch fuel prices
* Store raw payload
* Normalize stations and prices
* Upsert stations
* Insert deduplicated price observations
* Update latest_price

---

## ⏭️ Next Steps

Step 6 — Scheduler

* Periodic ingestion
* DB locking (ShedLock)

Step 7 — Geo API

* `/v1/stations/nearby`
* PostGIS queries (`ST_DWithin`, `ST_Distance`)

Step 8 — Station APIs

* Station detail
* Price history

Step 9 — Testing

* Parsing tests
* Deduplication tests
* Integration tests
* Testcontainers

Step 10 — Documentation

* ADRs
* API docs
* Data model docs

Step 11 — Client

* Android (Kotlin) or PWA

Step 12 — Cloud

* AWS deployment
* Terraform
* Monitoring (Grafana)
* CI/CD

---

# ⚙️ Running Locally

Start database:

```id="v8w4s0"
docker compose up -d
```

Run backend:

```id="v8qk9n"
cd backend
./gradlew bootRun
```

Health check:

```id="q5r0c1"
http://localhost:8080/actuator/health
```

---

# 🔑 Environment Variables

Fuel Finder API requires OAuth credentials:

```id="u4z7ks"
FUEL_FINDER_CLIENT_ID=your_client_id
FUEL_FINDER_CLIENT_SECRET=your_client_secret
```

These are loaded via:

```id="2q5p0l"
application-local.yml
application-prod.yml
```

---

# 💡 Why This Project Matters

This project demonstrates:

* Real-world data ingestion pipelines
* OAuth2 API integration
* External API resilience patterns
* Token caching strategies
* Geospatial querying with PostGIS
* Append-only data modeling
* Idempotent processing
* Clean backend architecture
* Production-style logging and configuration
* Infrastructure with Docker
* Database migrations with Flyway

This is not a tutorial project — it is designed to simulate a **production-ready backend system**.

---

# 👤 Author

**Fabrizio Bellisai**
Backend Java Engineer
Java • Spring Boot • PostgreSQL • PostGIS • Docker • AWS

---

# 📄 License

MVP project for educational and portfolio purposes.
