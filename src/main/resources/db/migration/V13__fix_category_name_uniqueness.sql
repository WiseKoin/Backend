-- Category names are user-owned. Older databases created by Hibernate may still have a
-- global UNIQUE (name) constraint, which prevents a second user from receiving the same
-- default categories during registration.
DO $$
DECLARE
    legacy_constraint RECORD;
BEGIN
    FOR legacy_constraint IN
        SELECT constraint_info.conname
        FROM pg_constraint constraint_info
        JOIN pg_class table_info
          ON table_info.oid = constraint_info.conrelid
        JOIN pg_namespace schema_info
          ON schema_info.oid = table_info.relnamespace
        WHERE schema_info.nspname = current_schema()
          AND table_info.relname = 'categories'
          AND constraint_info.contype = 'u'
          AND (
              SELECT array_agg(attribute_info.attname::text ORDER BY key_column.ordinality)
              FROM unnest(constraint_info.conkey) WITH ORDINALITY
                  AS key_column(attnum, ordinality)
              JOIN pg_attribute attribute_info
                ON attribute_info.attrelid = constraint_info.conrelid
               AND attribute_info.attnum = key_column.attnum
          ) = ARRAY['name']::text[]
    LOOP
        EXECUTE format(
            'ALTER TABLE %I.%I DROP CONSTRAINT %I',
            current_schema(),
            'categories',
            legacy_constraint.conname
        );
    END LOOP;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint constraint_info
        JOIN pg_class table_info
          ON table_info.oid = constraint_info.conrelid
        JOIN pg_namespace schema_info
          ON schema_info.oid = table_info.relnamespace
        WHERE schema_info.nspname = current_schema()
          AND table_info.relname = 'categories'
          AND constraint_info.contype = 'u'
          AND (
              SELECT array_agg(attribute_info.attname::text ORDER BY attribute_info.attname)
              FROM unnest(constraint_info.conkey) AS key_column(attnum)
              JOIN pg_attribute attribute_info
                ON attribute_info.attrelid = constraint_info.conrelid
               AND attribute_info.attnum = key_column.attnum
          ) = ARRAY['name', 'user_id']::text[]
    ) THEN
        ALTER TABLE categories
            ADD CONSTRAINT uq_category_user_name UNIQUE (user_id, name);
    END IF;
END
$$;
