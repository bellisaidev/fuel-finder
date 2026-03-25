create extension if not exists "pgcrypto";

create table raw_feed_fetch (
    id uuid primary key default gen_random_uuid(),
    retailer_id uuid not null references retailer(id),
    feed_type varchar(50) not null,
    endpoint_path varchar(255) not null,
    batch_number integer not null,
    fetched_at timestamptz not null,
    record_count integer not null,
    source_hash varchar(64) not null,
    raw_json jsonb not null
);

create index idx_raw_feed_fetch_feed_type_fetched_at
    on raw_feed_fetch (feed_type, fetched_at desc);

create index idx_raw_feed_fetch_source_hash
    on raw_feed_fetch (source_hash);

create index idx_raw_feed_fetch_batch_number
    on raw_feed_fetch (batch_number);

create index idx_raw_feed_fetch_retailer_id
    on raw_feed_fetch (retailer_id);