CREATE TABLE restaurant_branch_operating_hours (
    branch_id VARCHAR(36) NOT NULL,
    day_of_week TINYINT UNSIGNED NOT NULL,
    opens_at TIME NULL,
    closes_at TIME NULL,
    is_closed BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_restaurant_branch_operating_hours PRIMARY KEY (branch_id, day_of_week),
    CONSTRAINT fk_restaurant_branch_operating_hours_branch FOREIGN KEY (branch_id)
        REFERENCES restaurant_branch (branch_id) ON DELETE CASCADE,
    CONSTRAINT ck_restaurant_branch_operating_hours_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_restaurant_branch_operating_hours_time CHECK (
        (is_closed = TRUE AND opens_at IS NULL AND closes_at IS NULL)
        OR
        (is_closed = FALSE AND opens_at IS NOT NULL AND closes_at IS NOT NULL AND opens_at <> closes_at)
    )
) ENGINE=InnoDB;
