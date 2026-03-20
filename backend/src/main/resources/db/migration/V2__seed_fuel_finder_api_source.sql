INSERT INTO retailer (
    id,
    name,
    feed_url,
    is_active,
    fetch_interval_seconds
)
VALUES (
    gen_random_uuid(),
    'FUEL_FINDER_API',
    'https://www.fuel-finder.service.gov.uk/api/v1',
    true,
    1800
)
ON CONFLICT (name) DO NOTHING;