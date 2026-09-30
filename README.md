# Fixed Asset Backend — Spring Boot + SQL Server

Spring Boot/JdbcTemplate backend for the Fixed Asset frontend. It uses the existing `F2Database` and preserves the frontend API contract.

## API

- `GET /api/health`
- `GET /api/assets`
- `GET /api/import-history`
- `POST /api/upload` (multipart field `file`)
- `POST /api/upload-from-url` (JSON `{ "url": "..." }`)

## Database setup

Run `sql/01_create_database_and_tables.sql` in the existing `F2Database`. The script creates only `dbo.F2_FIXED_ASSET_IMPORT_HISTORY` when missing. It does not create, alter, clear, or replace `dbo.F2_FIXED_ASSET` or `dbo.F2_FIXED_ASSET_HISTORY`.

Configure credentials with environment variables:

```bat
set DB_URL=jdbc:sqlserver://YOUR_SERVER:1433;databaseName=F2Database;encrypt=true;trustServerCertificate=true
set DB_USERNAME=YOUR_USER
set DB_PASSWORD=YOUR_PASSWORD
set CORS_ALLOWED_ORIGINS=http://localhost:5173
mvn spring-boot:run
```

## Import behavior

The importer prefers `Details`, then a sheet containing `detail`, then the first sheet. It searches the first 20 rows for the header, requires `fI` (or a supported identifier alias), deduplicates by `MachineCode`, and maps only workbook columns that exist.

In one transaction it writes the successful import log, batch-updates matching `MachineCode` rows, and batch-inserts new rows. It never deletes master rows absent from Excel and never writes import snapshots to `F2_FIXED_ASSET_HISTORY`. Failed attempts are logged best-effort in a separate transaction.

## Build

```bat
mvn clean compile
mvn test
```
"# f2-fix-assets-be-spring" 
