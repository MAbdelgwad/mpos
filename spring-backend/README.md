# MPOS Spring migration — first increment

This is an **incomplete migration**, not a replacement for the Laravel application yet. It adds a separately runnable Java 21 / Spring Boot API with PostgreSQL, Flyway, Docker Compose, opaque bearer authentication and warehouse CRUD. The existing React source remains unchanged and is not switched to this API yet. No production data is imported.

## Run locally with Docker

From this directory:

```bash
cp .env.example .env
# Edit .env: set unique database and administrator passwords before starting.
docker compose up --build -d
docker compose logs -f api
curl http://localhost:8080/api/health
```

The API binds to loopback port 8080. PostgreSQL is internal to the Compose network and persists in a named volume. The administrator is inserted on first startup if the configured email does not exist. Changing the bootstrap password afterward does not reset an existing user's password. There is no public registration endpoint. Remove bootstrap credentials from the runtime configuration once an operational user-management flow exists.

## Implemented HTTP contract

| Method | Path | Result |
| --- | --- | --- |
| GET | `/api/health` | Liveness only; not a database readiness check |
| POST | `/api/login` | `{email,password}` → `data.token`, `data.user`, `data.permissions` |
| POST | `/api/logout` | Revokes the current bearer token |
| GET | `/api/warehouses` | `data[]`, `meta.total`, pagination metadata |
| GET | `/api/warehouses/{id}` | `data.id`, `data.type`, `data.attributes` |
| POST | `/api/warehouses` | Creates a warehouse; 201 |
| PUT/PATCH/POST | `/api/warehouses/{id}` | Replaces warehouse fields; JSON payload required |
| DELETE | `/api/warehouses/{id}` | Deletes a warehouse |

Authenticated endpoints require `Authorization: Bearer <token>`. Warehouse access requires ADMIN. VIEWER is a testable authorization boundary, not a replacement for the legacy configurable roles system. Tokens expire after two hours and only SHA-256 hashes are stored. Passwords use BCrypt.

List queries preserve `page[number]`, `page[size]`, `filter[search]`, and `sort` from the existing React code. Size zero returns all warehouses for dropdowns. Sorting is restricted to explicit columns. Search is case-insensitive and treats `%` and `_` literally. Update is full replacement, including the legacy POST alias. Optional blank email/zip fields become null. Names and emails are unique; duplicate entries return 409. Validation returns 422. Missing records return 404.

Example login (substitute your local credentials):

```bash
curl -X POST http://localhost:8080/api/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@example.com","password":"YOUR_LOCAL_PASSWORD"}'
```

Warehouse JSON:

```json
{"name":"المخزن الرئيسي","phone":"0501234567","country":"Saudi Arabia","city":"Riyadh","email":"","zip_code":"00123"}
```

## Verify

With Java 21 and Maven 3.9+, run `mvn verify`. Integration tests start an isolated real PostgreSQL database using Zonky Embedded Postgres; Docker and a separate database installation are not required. Tests must run as a non-root user with native PostgreSQL runtime prerequisites (Linux: libc, libstdc++, libgcc, zlib). First run needs Maven Central access. PostgreSQL test binaries are dependency-managed and may differ from the Compose PostgreSQL 17 image; validate the Compose image separately before release.

The image build packages the application but does not execute integration tests. Run `mvn verify` before building a release image.

## Not implemented yet

React standalone hosting and startup/settings contracts; configurable users/roles/permissions; password reset and login rate limiting; product catalog and uploads; stock, purchases, sales, returns, payments, POS registers, holds, quotations, coupons; reports, PDF/Excel exports, mail/SMS and localization/settings. See `../docs/spring-migration-ar.md` for the conversion map. Do not point the complete React application at this API yet: its login flow immediately requests permissions, settings, and dashboard APIs that have not been migrated.
