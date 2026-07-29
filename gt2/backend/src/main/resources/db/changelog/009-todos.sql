--liquibase formatted sql

--changeset casey:011-todos
-- Short-lived actionable items, tagged work or personal so the list can be filtered to one side
-- of the day. Separate from plan_items, which are the fixed 4-year roadmap. Nothing is seeded —
-- rows are created through the app and live only in the database.
CREATE TABLE todos (
  id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  title         VARCHAR(300) NOT NULL,
  kind          VARCHAR(10) NOT NULL DEFAULT 'personal' CHECK (kind IN ('work', 'personal')),
  done          BOOLEAN NOT NULL DEFAULT FALSE,
  due_date      DATE,
  sort_order    INT NOT NULL DEFAULT 0,
  completed_at  TIMESTAMPTZ,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- The list is always read filtered by kind and ordered by done, so index the leading columns.
CREATE INDEX idx_todos_kind_done ON todos (kind, done, sort_order);
--rollback DROP TABLE todos;
