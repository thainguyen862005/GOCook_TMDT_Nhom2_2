-- READ ONLY. Investigate rows returned before applying migration.
SELECT lower(trim(email)) AS normalized_email, count(*) AS duplicates
FROM users GROUP BY lower(trim(email)) HAVING count(*) > 1;
SELECT count(*) AS email_not_verified FROM users WHERE email_verified = FALSE;
SELECT count(*) AS total_users FROM users;
