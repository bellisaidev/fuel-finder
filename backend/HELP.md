# Backend Notes

The main project documentation lives in the repository root [README.md](../README.md).

This file is intentionally short and focused on backend-specific quick references.

## Run Profiles

- `local`: normal local development profile, scheduler enabled
- `local-manual`: manual ingestion profile, scheduler disabled, `IngestionRunner` executes once on startup
- `prod`: production-oriented settings
- `test`: lightweight test profile used by `BackendApplicationTests`

## Useful Commands

From [`backend/`](.) on Windows PowerShell:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local-manual"
```

```powershell
.\gradlew.bat test
```

## Geospatial API

Available station endpoints:

- `GET /v1/stations/nearby`
- `GET /v1/stations/cheapest-nearby`

Both endpoints accept:

- `lat`
- `lon`
- `radiusMeters`
- `fuelType`
- `limit` (optional, default `10`, max `100`)

## Station Location Fields

The normalized `station` model now stores:

- `address` as `address_line_1`
- `city`
- `county`
- `country`
- `postcode`
- `location` as PostGIS geography point

These fields are populated from the PFS feed normalization flow and exposed by the geospatial API.
