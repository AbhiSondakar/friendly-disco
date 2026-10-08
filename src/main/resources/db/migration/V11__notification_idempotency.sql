-- Flyway V11: Notification idempotency partial unique index.
-- Precondition cleans duplicate (user_id, type, reference_id) rows.
-- Survivor per duplicate group = newest by (created_at DESC, id DESC).
-- If any row in a duplicate group is unread, survivor is forced to is_read=false.

DO $$
DECLARE
    rows_to_delete INTEGER;
    mixed_groups INTEGER;
    rows_flipped INTEGER;
    abort_threshold CONSTANT INTEGER := 10000;
BEGIN
    -- 1. Count total rows to be removed (sum over duplicate rows minus 1 survivor per group).
    SELECT COALESCE(SUM(cnt - 1), 0)
      INTO rows_to_delete
      FROM (
          SELECT COUNT(*) AS cnt
            FROM notifications
           WHERE reference_id IS NOT NULL
           GROUP BY user_id, type, reference_id
          HAVING COUNT(*) > 1
      ) dup_groups;

    IF rows_to_delete > abort_threshold THEN
        RAISE EXCEPTION
            'Flyway V11 Precondition Aborted: % duplicate notification rows (sum of cnt-1 per group) exceed threshold of %',
            rows_to_delete, abort_threshold;
    END IF;

    IF rows_to_delete > 0 THEN
        RAISE WARNING 'Flyway V11 Precondition: % duplicate notification rows will be removed (threshold ok; survivor = newest created_at DESC, id DESC)', rows_to_delete;
    ELSE
        RAISE WARNING 'Flyway V11 Precondition: No duplicate (user_id, type, reference_id) rows found';
    END IF;

    -- 2. Count duplicate groups with mixed is_read AND at least one unread row.
    --    For each such group, force the (newest) survivor to is_read=false so unread state is not lost.
    IF rows_to_delete > 0 THEN
        WITH survivor_candidates AS (
            SELECT DISTINCT ON (user_id, type, reference_id)
                   user_id, type, reference_id, id AS survivor_id,
                   (NOT BOOL_AND(is_read) OVER w_partition) AS has_any_unread_in_group
              FROM notifications
              WHERE reference_id IS NOT NULL
              WINDOW w_partition AS (PARTITION BY user_id, type, reference_id)
              ORDER BY user_id, type, reference_id, created_at DESC, id DESC
        ),
        duplicate_groups AS (
            SELECT user_id, type, reference_id
              FROM notifications
             WHERE reference_id IS NOT NULL
             GROUP BY user_id, type, reference_id
            HAVING COUNT(*) > 1
        )
        SELECT COALESCE(COUNT(*), 0)
          INTO mixed_groups
          FROM survivor_candidates sc
          JOIN duplicate_groups dg USING (user_id, type, reference_id)
         WHERE sc.has_any_unread_in_group = TRUE;

        IF mixed_groups > 0 THEN
            WITH survivor_candidates AS (
                SELECT DISTINCT ON (user_id, type, reference_id)
                       user_id, type, reference_id, id AS survivor_id,
                       (NOT BOOL_AND(is_read) OVER w_partition) AS has_any_unread_in_group
                  FROM notifications
                  WHERE reference_id IS NOT NULL
                  WINDOW w_partition AS (PARTITION BY user_id, type, reference_id)
                  ORDER BY user_id, type, reference_id, created_at DESC, id DESC
            ),
            duplicate_groups AS (
                SELECT user_id, type, reference_id
                  FROM notifications
                 WHERE reference_id IS NOT NULL
                 GROUP BY user_id, type, reference_id
                HAVING COUNT(*) > 1
            )
            UPDATE notifications n
               SET is_read = FALSE
              FROM survivor_candidates sc
              JOIN duplicate_groups dg USING (user_id, type, reference_id)
             WHERE n.id = sc.survivor_id
               AND sc.has_any_unread_in_group = TRUE
               AND n.is_read = TRUE;

            GET DIAGNOSTICS rows_flipped = ROW_COUNT;

            RAISE WARNING 'Flyway V11 Precondition: % mixed-is_read duplicate groups adjusted; flipped % survivor row(s) is_read=false to preserve unread state',
                mixed_groups, rows_flipped;
        END IF;

        -- 3. Delete all non-survivor rows from duplicate groups (survivor = newest created_at DESC, id DESC).
        DELETE FROM notifications
         WHERE reference_id IS NOT NULL
           AND id NOT IN (
               SELECT DISTINCT ON (user_id, type, reference_id) id
                 FROM notifications
                WHERE reference_id IS NOT NULL
                ORDER BY user_id, type, reference_id, created_at DESC, id DESC
           );
    END IF;
END $$;

-- Idempotency: duplicate events produce exactly one notification row per recipient per (type, reference_id).
CREATE UNIQUE INDEX IF NOT EXISTS uq_notifications_user_type_ref
    ON notifications (user_id, type, reference_id)
    WHERE reference_id IS NOT NULL;

-- idx_notifications_user_created_at was already created in V9 (notifications_user_created_at_idx).
-- Per-step-guard; skipped (this line so no-op here.
