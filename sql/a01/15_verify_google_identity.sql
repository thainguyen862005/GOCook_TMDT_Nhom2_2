-- Read-only verification. New Google accounts have password_login_enabled=false until the user sets a password.
SELECT to_regclass('public.user_auth_identities') AS identity_table;
SELECT column_name, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema='public' AND table_name='users' AND column_name IN ('email_verified','password_login_enabled');
SELECT password_login_enabled, email_verified, count(*)
FROM public.users GROUP BY password_login_enabled, email_verified ORDER BY 1,2;
SELECT provider, count(*) FROM public.user_auth_identities GROUP BY provider;
-- Must produce zero rows:
SELECT provider, subject, count(*) FROM public.user_auth_identities
GROUP BY provider,subject HAVING count(*)>1;
