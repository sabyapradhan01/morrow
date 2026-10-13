# Morrow Cloud Admin Setup & Demo

1. Create a Neon project and run `database/01_schema_postgresql.sql` in Neon SQL Editor.
2. Deploy this repository through Render Blueprint using `render.yaml`.
3. Set `NEON_JDBC_URL`, `NEON_DB_USER`, and `NEON_DB_PASSWORD` in Render's Environment page. Keep `MORROW_ADMIN_SQL_ENABLED=false` for a public site.
4. Register the first intended administrator on the live website.
5. In Neon SQL Editor, update the email in `database/02_bootstrap_admin_postgresql.sql` and run it once. Verify the intended account's `role` is `ADMIN`.
6. Sign out and open `/admin`. Use the promoted account credentials.
7. Test the user flow: register a second user, add simulated funds, transfer funds, and inspect `wallets`, `transactions`, and `ledger` in Neon SQL Editor.
8. Test admin functionality with disposable data: view/edit users, make a wallet adjustment through the UI, inspect the generated transaction/ledger rows, and close the test account.

Do not enable arbitrary SQL execution on the public host. The workbench is disabled by default through `MORROW_ADMIN_SQL_ENABLED=false`; it can be enabled only for trusted local development. Funds are simulated.
