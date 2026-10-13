# Morrow architecture (Phase 1)

## Stack
- Java 21
- Spring Boot 3.5.16
- Spring MVC + Thymeleaf for server-rendered pages
- Spring JDBC (`JdbcTemplate`) for parameterized SQL and ordinary JDBC transaction support
- Oracle Database via Oracle JDBC Thin driver (`com.oracle.database.jdbc:ojdbc8:12.2.0.1`)
- Maven for dependency management and builds

## Layers (target architecture)

```text
Browser
  -> Spring MVC controllers
  -> service layer (wallet and authentication rules)
  -> repository layer (JdbcTemplate / SQL)
  -> Oracle Database
```

We intentionally use JDBC, not JPA/Hibernate, because PAT 4 specifically requires demonstrating JDBC. `JdbcTemplate` is a Spring helper around the JDBC API; all SQL statements and transaction boundaries remain visible in the Java code.

## Planned features
- Register, log in, and log out
- Dashboard, current wallet balance, transaction history
- Simulated top-up
- Transfers between registered users with sufficient-balance checks
- Transaction and ledger records, DDL/DML scripts, and test documentation

All money is simulated for academic demonstration. No real bank connection or real-money payment processing is included.

## Transaction rule
Transfers must run inside one database transaction. The implementation will lock/update wallets safely, check sufficient available funds in the service layer and database, create transaction and ledger entries, and commit only after every step succeeds; otherwise rollback all changes.


## Role-based administration

The `USERS.ROLE` column defines `USER` and `ADMIN`. Self-registration always assigns `USER`. The regular login endpoint accepts only `USER`; `/api/auth/admin-login` accepts only `ADMIN`. The `/api/admin/**` controller reloads the current active role from Oracle for each request and rejects non-admin sessions. The browser page at `/admin` is only a UI; authorization is enforced in Java, not by hiding buttons.

Admin tools include user creation/editing/soft-closing, audited wallet target-balance adjustment, transaction description editing/deletion with linked ledger cleanup and wallet-balance rebuild, ledger edit/deletion with balance rebuild, and a one-statement SQL workbench. The SQL workbench is local-academic only and runs with `MORROW_APP` database privileges, not `SYS`/`SYSTEM` privileges.
