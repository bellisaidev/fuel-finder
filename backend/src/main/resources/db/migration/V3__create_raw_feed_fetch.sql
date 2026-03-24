create table raw_feed_fetch (
    id bigserial primary key,
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