-- Preserve inactive profiles and keep the earliest active profile as the tenant's active profile.
UPDATE restaurant_profile AS profile_row
JOIN (
    SELECT MIN(restaurant_id) AS keep_restaurant_id
    FROM restaurant_profile
    WHERE is_active = TRUE
) AS active_profile ON TRUE
SET profile_row.is_active = FALSE
WHERE profile_row.is_active = TRUE
  AND profile_row.restaurant_id <> active_profile.keep_restaurant_id;

ALTER TABLE restaurant_profile
    ADD COLUMN active_profile_key VARCHAR(16)
        GENERATED ALWAYS AS (CASE WHEN is_active = TRUE THEN 'ACTIVE' ELSE NULL END) STORED;

CREATE UNIQUE INDEX uk_restaurant_profile_one_active
    ON restaurant_profile (active_profile_key);
