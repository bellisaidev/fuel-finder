-- Fuel Finder MVP - initial schema (Postgres + PostGIS)

-- Extensions
CREATE EXTENSION IF NOT EXISTS postgis;

-- =========
-- Retailers (feed registry)
-- =========
CREATE TABLE retailer (
  id           UUID PRIMARY KEY,
  name         TEXT NOT NULL UNIQUE,
  feed_url     TEXT NOT NULL,
  is_active    BOOLEAN NOT NULL DEFAULT TRUE,
  fetch_interval_seconds INT NOT NULL DEFAULT 3600,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =========
-- Raw fetch audit (debugging + traceability)
-- =========
CREATE TABLE raw_feed_fetch (
  id            UUID PRIMARY KEY,
  retailer_id   UUID NOT NULL REFERENCES retailer(id),
  fetched_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  http_status   INT NOT NULL,
  etag          TEXT NULL,
  last_modified TEXT NULL,
  payload_hash  TEXT NOT NULL,
  payload       JSONB NOT NULL,
  parse_errors  JSONB NULL,
  station_count INT NULL
);

CREATE INDEX idx_raw_feed_fetch_retailer_fetched_at
  ON raw_feed_fetch (retailer_id, fetched_at DESC);

-- =========
-- Stations
-- =========
CREATE TABLE station (
  id         UUID PRIMARY KEY,
  retailer_id UUID NOT NULL REFERENCES retailer(id),
  site_id    TEXT NOT NULL,
  brand      TEXT NULL,
  address    TEXT NULL,
  postcode   TEXT NULL,
  location   GEOGRAPHY(POINT, 4326) NULL,
  is_active  BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT uq_station_retailer_site UNIQUE (retailer_id, site_id)
);

CREATE INDEX idx_station_location_gist
  ON station USING GIST (location);

CREATE INDEX idx_station_postcode
  ON station (postcode);

-- =========
-- Price observations (append-only)
-- =========
CREATE TABLE price_observation (
  id                 UUID PRIMARY KEY,
  station_id         UUID NOT NULL REFERENCES station(id),
  fuel_type          TEXT NOT NULL,
  price_pence        INT NOT NULL,
  currency           TEXT NOT NULL DEFAULT 'GBP',
  observed_at        TIMESTAMPTZ NOT NULL DEFAULT now(),      -- when we observed it
  reported_updated_at TIMESTAMPTZ NULL,                       -- timestamp from feed (not trusted)
  source_hash        TEXT NOT NULL,                           -- dedupe key
  raw_payload_id     UUID NULL REFERENCES raw_feed_fetch(id),

  CONSTRAINT chk_price_pence_positive CHECK (price_pence > 0)
);

-- Dedupe: avoid duplicating same station+fuel+price for same normalized record
CREATE UNIQUE INDEX uq_price_observation_dedupe
  ON price_observation (station_id, fuel_type, source_hash);

-- Query support
CREATE INDEX idx_price_obs_station_fuel_observed
  ON price_observation (station_id, fuel_type, observed_at DESC);

CREATE INDEX idx_price_obs_fuel_observed
  ON price_observation (fuel_type, observed_at DESC);

-- =========
-- Latest price read-model (fast /nearby)
-- =========
CREATE TABLE latest_price (
  station_id          UUID NOT NULL REFERENCES station(id),
  fuel_type           TEXT NOT NULL,
  price_pence         INT NOT NULL,
  currency            TEXT NOT NULL DEFAULT 'GBP',
  observed_at         TIMESTAMPTZ NOT NULL,
  reported_updated_at TIMESTAMPTZ NULL,

  PRIMARY KEY (station_id, fuel_type)
);

CREATE INDEX idx_latest_price_fuel_price
  ON latest_price (fuel_type, price_pence ASC);

-- =========
-- Seed (optional): keep empty for now
-- =========
