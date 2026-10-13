-- Run in Neon SQL Editor only AFTER registering the first account through the live website.
-- Replace the email below with the exact email of the account you want to promote.
-- This does not alter the account password or wallet.
UPDATE users
SET role = 'ADMIN'
WHERE LOWER(email) = LOWER('replace-with-your-admin-email@example.com')
  AND status = 'ACTIVE';

-- Expected: UPDATE 1. If it says UPDATE 0, check the email and account status.
SELECT user_id, full_name, email, role, status
FROM users
ORDER BY user_id;
