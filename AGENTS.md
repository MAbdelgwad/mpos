# Repository implementation rules

- Develop the deployed application in `backend/` and `frontend/`. Treat `legacy/laravel/` as reference only.
- Organize backend packages by business module, then api/application/domain/infrastructure where needed.
- Controllers adapt HTTP only. Put transactions and use-case coordination in application services.
- Keep domain code independent of Spring, JDBC and HTTP. Map BusinessException in shared API advice.
- Purchasing and inventory application services must use persistence ports. Do not bypass StockOperations to modify stock from another module.
- Preserve row locks, idempotency and transaction boundaries. Do not edit released Flyway migrations.
- Do not expand transitional JDBC services unnecessarily; extract cohesive repositories when changing their persistence behavior. See docs/architecture-ar.md for current limitations.
- Preserve existing API contracts while migrating Map payloads to explicit DTOs.
- Validate backend changes with `cd backend && mvn verify`; validate frontend changes with `cd frontend && npm run build`.
- Never include .env, dependencies, build outputs or real customer data in commits or downloadable packages.
