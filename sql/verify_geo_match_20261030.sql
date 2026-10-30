-- Read-only verification; DO NOT replace production data with guessed coordinates.
SELECT
  COUNT(*) AS chef_profiles_total,
  COUNT(*) FILTER (WHERE service_area ~ '^([0-9]{2})[|][0-9.-]+[|][0-9.-]+[|](5|10|15|20|30|40|60)$') AS chef_locations_structured,
  COUNT(*) FILTER (WHERE service_area IS NULL OR service_area !~ '^([0-9]{2})[|][0-9.-]+[|][0-9.-]+[|](5|10|15|20|30|40|60)$') AS chef_need_location_confirmation
FROM chef_profiles;

SELECT COUNT(*) AS legacy_open_requests_need_location_confirmation
FROM public_cooking_requests
WHERE status <> 'CLOSED'
  AND (requested_area IS NULL OR requested_area !~ '^[0-9]{2}[|][0-9.-]+[|][0-9.-]+$');
