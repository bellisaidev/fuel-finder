# 🚀 Fuel Finder (MVP)

Geo-based fuel price aggregator built on UK Fuel Finder Scheme open data.

Fuel Finder is a backend-focused MVP designed to aggregate fuel price data from multiple retailers and expose geospatial APIs to find the cheapest fuel nearby.

The project focuses on data correctness, auditability, and scalable design, rather than UI polish.

---

# 🎯 Project Goal

Build a public MVP (potential startup) that:

- Aggregates fuel price data from UK Fuel Finder Scheme
- Stores full historical price data per station
- Provides geo-based APIs for “cheapest fuel near me”
- Is designed to scale without premature overengineering

---

# 🧱 Tech Stack

## Backend
- Java 21
- Spring Boot 3
- Spring Web
- Spring Data JPA
- Hibernate + Hibernate Spatial

## Database
- PostgreSQL
- PostGIS

## Data & Migrations
- Flyway

## DevOps
- Docker
- Docker Compose

## Observability
- Spring Boot Actuator

---

# 🧠 Architecture

- Modular Monolith
- No microservices (MVP phase)
- No Kafka (MVP phase)

Principles:
- Keep it simple
- Focus on correctness
- Avoid premature complexity

---

# 📊 Data Model

Core entities:

- retailer → data source
- raw_feed_fetch → raw JSON + audit trail
- station → fuel station with geo location
- price_observation → append-only price history
- latest_price → read model for fast queries

Key design decisions:

- Append-only price model
- observed_at = ingestion time (source of truth)
- Feed timestamps are not trusted
- DB-level deduplication via source_hash
- PostGIS geography(Point,4326) for geo queries

---

# 🔄 Ingestion Pipeline

End-to-end ingestion flow:

1. Fetch retailer JSON feed
2. Store raw payload (raw_feed_fetch)
3. Parse & normalize data
4. Upsert stations
5. Insert price observations (idempotent)
6. Update latest_price

Guarantees:

- Idempotent ingestion
- Full audit trail
- No duplicate price entries
- Historical data preserved

---

# 🌍 API

## Nearby stations

GET /v1/stations/nearby?lat=&lon=&radiusMeters=&fuelType=&sort=price|distance

Returns:

- station id, brand, address, postcode
- lat/lon
- distance_meters
- fuel_type
- price_pence
- observed_at

---

## Station detail

GET /v1/stations/{id}

---

## Price history

GET /v1/stations/{id}/prices?fuelType=&from=&to=&page=

---

# 🗺️ Roadmap

## ✅ Completed

- Project structure initialized
- Docker Compose (Postgres + PostGIS)
- Spring Boot backend running
- Flyway migrations
- Database schema created
- JPA entities & repositories
- Hibernate Spatial configured

---

## 🚧 In Progress

Step 5 — Ingestion pipeline

- Fetch real retailer feed
- Store raw payload
- Normalize stations and prices
- Upsert stations
- Insert deduplicated price observations
- Update latest_price

---

## ⏭️ Next Steps

Step 6 — Scheduler
- Periodic ingestion
- DB locking (ShedLock)

Step 7 — Geo API
- /v1/stations/nearby
- PostGIS queries (ST_DWithin, ST_Distance)

Step 8 — Station APIs
- Station detail
- Price history

Step 9 — Testing
- Parsing tests
- Deduplication tests
- Integration tests

Step 10 — Documentation
- ADRs
- API docs
- Data model docs

Step 11 — Client
- Android (Kotlin) or PWA

---

# ⚙️ Running Locally

Start database:

docker compose up -d

Run backend:

cd backend
./gradlew bootRun

Health check:

http://localhost:8080/actuator/health

---

# 💡 Why This Project Matters

This project demonstrates:

- Real-world data ingestion pipelines
- Geospatial querying with PostGIS
- Append-only data modeling
- Idempotent processing
- Clean backend architecture

---

# 👤 Author

Fabrizio Bellisai  
Backend Java Engineer (8+ years experience)  
Based in Switzerland~~~~