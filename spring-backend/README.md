# MPOS Spring migration — catalog and access

Java 21 / Spring Boot 4.1.2, PostgreSQL, Flyway, and the existing React 17 screens served independently of PHP. This is a **partial migration**: master data and access management are implemented; financial/inventory workflows are not yet available. The Laravel entry point remains available in the repository.

## Start the new application

From `spring-backend`:

```bash
cp .env.example .env
# Edit .env and set unique database and administrator passwords (12+ characters).
docker compose up --build -d
docker compose logs -f api
```

Open **http://localhost:8081** and sign in using `MPOS_ADMIN_EMAIL` and `MPOS_ADMIN_PASSWORD`. Port 8080 exposes the API on loopback; PostgreSQL is internal to Compose. Data and images persist in PostgreSQL's named volume. Do not run `docker compose down -v` unless you intend to erase that database.

The bootstrap administrator is inserted only if its email does not exist. Changing bootstrap environment variables does not reset an existing account's password. Existing foundation databases are upgraded through V2; V1 is unchanged. This deployment does not import MySQL data.

## Available functionality

- Login/logout with two-hour revocable opaque bearer tokens; BCrypt password hashes and SHA-256 token hashes.
- Users, enabled/disabled state, role assignment, and custom roles with explicit permissions. Permission changes apply on the next request. Built-in ADMIN/VIEWER roles are protected; administrators cannot be disabled, demoted, or deleted through this API.
- Warehouse, brand, product category, base-unit, unit, customer, supplier, and product create/read/update/delete.
- Product costs/prices with four decimal places, tax validation, barcode format validation, and generated PNG barcodes. No financial posting is performed.
- Sale/purchase unit foreign keys enforce the selected product base unit, including under concurrent writes.
- PNG/JPEG uploads for products, brands, categories and users. Maximum 2 MB/image, 20 megapixels, five images/product. Bytes are stored in PostgreSQL. Images have public UUID URLs for compatibility with HTML image elements; images must not contain private documents.
- Company name, currency display symbol, footer, date format and default language settings. Arabic/English switching and RTL through the existing translations.
- Search, sorting and legacy `data.attributes`/`meta.total` responses. `page[size]=0` returns all records for dropdowns.
- Existing React master-data pages plus a new master-data landing page. Unsupported transaction/report/import/export controls are hidden in the Spring deployment.

## API map

| Resource | Methods |
| --- | --- |
| `/api/login`, `/api/logout` | POST; login accepts JSON or form data |
| `/api/validate-auth-token` | POST |
| `/api/config`, `/api/permissions`, `/api/catalog-summary` | GET |
| `/api/front-setting`, `/api/health` | Public GET |
| `/api/warehouses` | GET, POST; `/{id}` GET/PUT/PATCH/POST/DELETE |
| `/api/brands`, `/api/product-categories`, `/api/base-units`, `/api/units`, `/api/customers`, `/api/suppliers`, `/api/products` | GET, POST; `/{id}` GET/PUT/PATCH/POST/DELETE |
| `/api/users`, `/api/roles` | GET, POST; `/{id}` GET/PUT/PATCH/POST/DELETE |
| `/api/ui-settings` | GET, PUT |
| `/api/change-language` | POST JSON `{language:"ar"}` or `"en"` |
| `/api/change-password` | PATCH JSON: current_password, password, password_confirmation; revokes all sessions |
| `/api/media/{uuid}` | Public GET |
| `/api/products-image-delete/{uuid}` | DELETE with product permission |

Except public GETs and login, send `Authorization: Bearer <token>`. User updates require first_name, email, role_id and optional last_name/phone/enabled. New passwords require confirm_password and at least 12 characters / at most 72 UTF-8 bytes. Updates use full field replacement rather than JSON Merge Patch. Resource deletion is blocked when referenced. Role listing currently returns all roles; users list supports paging/search but uses stable ID ordering.

Roles with product-management permission also need the relevant brand/category/unit permissions to use the product form's dropdowns. User and role administration permissions intentionally allow managing access; only assign them to trusted administrators.

## Build and verify

```bash
# Java 21 JDK and Maven 3.9+
cd spring-backend
mvn verify
# From repository root, preserve the existing locked React dependency versions:
npm ci --legacy-peer-deps --no-audit --no-fund
npx mix --mix-config=webpack.spring.mix.js --production
```

Integration tests use isolated real PostgreSQL 14.22 via Zonky; run as a non-root user. Docker is not needed for these tests. Compose targets PostgreSQL 17 and still needs its own deployment check. Browser smoke is opt-in:

```bash
# From repository root, after building React:
npm install --prefix spring-frontend/tests
cd spring-frontend/tests
npx playwright install chromium
cd ../../spring-backend
mvn -Dmpos.browser=true verify
```

`MPOS_CHROMIUM` can select an installed Chromium binary. The browser test is designed to use a temporary same-origin HTTP proxy and the actual React bundle/API/database. It has NOT completed in the authoring environment: Chromium failed to launch because Unix sockets are disallowed. Run it locally before relying on end-to-end behavior. It does not test Nginx or Docker itself.

## Remaining migration work

Purchases and opening balances; inventory ledger/adjustments/transfers; sales/payments/returns; POS registers/holds/coupons; quotations/expenses; reports/PDF/Excel/import; mail/SMS/password-recovery flows; full user profile editing and production login rate limiting. Product stock values remain zero while the new database has no inventory workflows. Opening-stock parameters are rejected, not silently discarded. Do not use this version for live sales.

The frontend retains the repository's existing dependencies. The build reports existing CSS deprecation warnings; dependency modernization is a separate task. PostgreSQL tests and browser smoke do not substitute for financial parity tests or production deployment validation.
