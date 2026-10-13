-- MORROW / NEON POSTGRESQL - READ-ONLY DIAGNOSTIC QUERIES
-- Run in Neon SQL Editor after the schema has been created.

SELECT user_id, full_name, email, role, status, created_at
FROM users
ORDER BY user_id;

SELECT wallet_id, user_id, currency_code, balance, created_at
FROM wallets
ORDER BY wallet_id;

SELECT transaction_id, reference_code, transaction_type,
       sender_wallet_id, receiver_wallet_id, amount, status,
       description, created_at
FROM transactions
ORDER BY transaction_id DESC
LIMIT 100;

SELECT ledger_id, transaction_id, wallet_id, entry_type,
       amount, balance_after, created_at
FROM ledger
ORDER BY ledger_id DESC
LIMIT 200;

SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_type = 'BASE TABLE'
ORDER BY table_name;
