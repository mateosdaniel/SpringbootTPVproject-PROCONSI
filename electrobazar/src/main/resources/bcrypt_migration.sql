-- ============================================================
-- BCrypt Password Migration Script
-- ============================================================
-- This script MUST be executed ONCE against the live database
-- BEFORE restarting the application with BCrypt enabled.
--
-- The workers table's password column is already VARCHAR(255),
-- which is large enough for BCrypt hashes (60 chars). No DDL change needed.
--
-- For each existing worker with a plain-text password, replace it
-- with the pre-computed BCrypt hash of the same password.
--
-- HOW TO GENERATE BCrypt HASHES:
--   Option A – Spring Shell / quick Java snippet:
--       new BCryptPasswordEncoder().encode("yourPassword")
--
--   Option B – Online tool (e.g. https://bcrypt.online) with strength 10.
--
--   Option C – Run the helper below in a temporary Spring Boot CommandLineRunner
--              and copy the output.
--
-- EXAMPLE: if your "admin" worker has password "admin1234", replace with:
--   UPDATE workers
--   SET    password = '$2a$10$...<bcrypt_hash_here>...'
--   WHERE  username = 'admin';
--
-- ============================================================
-- STEP 1 – Verify current plain-text passwords (read-only, safe to run):
-- ============================================================
SELECT id, username,
       CASE
           WHEN password LIKE '$2a$%' OR password LIKE '$2b$%' THEN 'Already BCrypt ✓'
           ELSE CONCAT('PLAIN-TEXT (length=', CHAR_LENGTH(password), ') ✗')
       END AS password_status
FROM workers
ORDER BY id;

-- ============================================================
-- STEP 2 – For each worker listed above with PLAIN-TEXT status,
--           run an UPDATE like the template below.
--
-- Replace '<username>' and '<bcrypt_hash>' with real values.
-- ============================================================

-- TEMPLATE (repeat once per plain-text worker):
-- UPDATE workers
-- SET    password = '<bcrypt_hash>'   -- 60-char BCrypt hash, starts with $2a$10$
-- WHERE  username = '<username>'
--   AND  password NOT LIKE '$2%';     -- safety guard: never overwrite a hash

-- ============================================================
-- STEP 3 – Confirm all passwords are now hashed:
-- ============================================================
-- SELECT id, username, LEFT(password, 7) AS hash_prefix
-- FROM workers;
-- Expected: every row shows '$2a$10$' in hash_prefix.
-- ============================================================
