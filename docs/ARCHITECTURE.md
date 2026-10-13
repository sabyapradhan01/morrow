# Morrow Cloud Architecture

- Browser UI: Thymeleaf template plus static HTML/CSS/vanilla JavaScript.
- HTTP layer: Spring MVC controllers expose REST-style `/api/...` endpoints.
- Service layer: validates requests and enforces session/role and wallet rules.
- Data access: Spring `JdbcTemplate` with PostgreSQL JDBC driver (pgJDBC).
- Database: Neon PostgreSQL with `users`, `wallets`, `transactions`, and `ledger` tables.
- Deployment: Docker image built by Render from GitHub; configuration comes from environment variables.

Flow: Browser → Controller → Service → JdbcTemplate/pgJDBC → Neon PostgreSQL → HTTP response → refreshed UI.

`@Transactional` groups wallet, transaction, and ledger changes so an error rolls the unit of work back. Wallet rows are selected `FOR UPDATE` before balance changes. In hosted mode, unrestricted raw SQL execution is disabled; admin screens expose purpose-built operations.
