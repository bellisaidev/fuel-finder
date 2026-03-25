-- Fuel Finder MVP - initial schema (Postgres + PostGIS)

-- Extensions
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- =========
-- Retailers (feed registry)
-- =========
CREATE TABLE retailer (
  id                     UUID PRIMARY KEY,
  name                   TEXT NOT NULL UNIQUE,
  feed_url               TEXT NOT NULL,
  is_active              BOOLEAN NOT NULL DEFAULT TRUE,
  fetch_interval_seconds INT NOT NULL DEFAULT 3600,
  created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =========
-- Raw feed fetches (raw ingestion audit + replay + dedupe support)
-- =========
CREATE TABLE raw_feed_fetch (
  id            UUID PRIMARY KEY,
  retailer_id   UUID NOT NULL REFERENCES retailer(id),
  feed_type     VARCHAR(50) NOT NULL,
  endpoint_path VARCHAR(255) NOT NULL,
  batch_number  INTEGER NOT NULL,
  fetched_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  record_count  INTEGER NOT NULL,
  source_hash   VARCHAR(64) NOT NULL,
  raw_json      JSONB NOT NULL
);

CREATE INDEX idx_raw_feed_fetch_feed_type_fetched_at
  ON raw_feed_fetch (feed_type, fetched_at DESC);

CREATE INDEX idx_raw_feed_fetch_source_hash
  ON raw_feed_fetch (source_hash);

CREATE INDEX idx_raw_feed_fetch_batch_number
  ON raw_feed_fetch (batch_number);

CREATE INDEX idx_raw_feed_fetch_retailer_id
  ON raw_feed_fetch (retailer_id);

-- =========
-- Stations
-- =========
CREATE TABLE station (
  id          UUID PRIMARY KEY,
  retailer_id UUID NOT NULL REFERENCES retailer(id),
  site_id     TEXT NOT NULL,
  brand       TEXT NULL,
  address     TEXT NULL,
  postcode    TEXT NULL,
  location    GEOGRAPHY(POINT, 4326) NULL,
  is_active   BOOLEAN NOT NULL DEFAULT TRUE,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

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
  id                  UUID PRIMARY KEY,
  station_id          UUID NOT NULL REFERENCES station(id),
  fuel_type           TEXT NOT NULL,
  price_pence         INT NOT NULL,
  currency            TEXT NOT NULL DEFAULT 'GBP',
  observed_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  reported_updated_at TIMESTAMPTZ NULL,
  source_hash         TEXT NOT NULL,
  raw_payload_id      UUID NULL REFERENCES raw_feed_fetch(id),

  CONSTRAINT chk_price_pence_positive CHECK (price_pence > 0)
);

CREATE UNIQUE INDEX uq_price_observation_dedupe
  ON price_observation (station_id, fuel_type, source_hash);

CREATE INDEX idx_price_obs_station_fuel_observed
  ON price_observation (station_id, fuel_type, observed_at DESC);

CREATE INDEX idx_price_obs_fuel_observed
  ON price_observation (fuel_type, observed_at DESC);

-- =========
-- Latest price read-model (fast nearby queries)
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
