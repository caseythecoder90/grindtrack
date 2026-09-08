--liquibase formatted sql

--changeset casey:020-calendar
-- The calendar tab. Two independent shapes that the word "calendar" hides:
--
--   calendar_events   things that happen at a time
--   recurring_tasks   things due on a cadence, where the question is "when did I last"
--
-- They are separate tables because they answer different questions. An event is asked
-- for by date range; a recurring task is asked for by how overdue it is, and its next
-- due date is derived rather than stored, so it can never drift from last_done_on.
--
-- Nothing is seeded. This is personal content and the repo is public -- the same rule
-- plan_items and relationship_moments follow.

-- A date and an optional time, rather than a timestamptz.
--
-- "Sep 12 at 09:00" on a personal calendar means wall clock, not an instant: it is
-- 09:00 wherever you are, and it does not shift when the server's zone does. Storing
-- an instant would force every read to convert back, which is exactly the bug the
-- money tab already hit once by building dates in UTC. A date column also makes
-- "what is on this day" an equality rather than a range with a zone in it.
CREATE TABLE calendar_events (
  id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  title        VARCHAR(300) NOT NULL,
  kind         VARCHAR(20) NOT NULL
                 CHECK (kind IN ('appointment', 'study_block', 'work_block', 'personal')),
  event_date   DATE NOT NULL,

  -- NULL start_time is what "all day" means; there is no separate flag to disagree
  -- with it. An end without a start is nonsense, and so is an end before one.
  start_time   TIME,
  end_time     TIME,
  CONSTRAINT calendar_events_time_order CHECK (
    end_time IS NULL OR (start_time IS NOT NULL AND end_time > start_time)
  ),

  -- What this block is against: the plan item the morning was booked for. This is
  -- recorded INTENT, and it is deliberately not the same thing as a focus session's
  -- subject -- only the lunch kinds carry one of those, because letting a study
  -- session claim a subject is exactly the leak migration 018 had to repair.
  --
  -- Keeping the two separate is what makes planned-versus-actual answerable at all:
  -- this column says what the block was for, focus_sessions say what actually
  -- happened, and the gap between them is the interesting number.
  --
  -- ON DELETE SET NULL, not CASCADE: re-importing the workbook removes items the plan
  -- no longer contains, and that must not silently delete the mornings already booked
  -- against them. The block survives, unlinked, with its title intact.
  plan_item_id BIGINT REFERENCES plan_items (id) ON DELETE SET NULL,

  notes        TEXT NOT NULL DEFAULT '',
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Every read is a date range (a month grid, a day sheet), ordered within the day.
CREATE INDEX idx_calendar_events_date ON calendar_events (event_date, start_time);
CREATE INDEX idx_calendar_events_plan_item ON calendar_events (plan_item_id)
  WHERE plan_item_id IS NOT NULL;

-- Upkeep: the dog's flea and tick, the HVAC filter, the oil change.
--
-- next_due is deliberately NOT a column. It is last_done_on + interval_days, and the
-- moment it is stored as well as derived the two can disagree -- which is the failure
-- that makes a reminder app untrustworthy exactly once.
CREATE TABLE recurring_tasks (
  id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  title         VARCHAR(200) NOT NULL,
  category      VARCHAR(20) NOT NULL
                  CHECK (category IN ('pet', 'home', 'health', 'car', 'other')),
  interval_days INT NOT NULL CHECK (interval_days BETWEEN 1 AND 3650),

  -- NULL means never done, which reads as due now. That is the right default: a task
  -- worth adding is a task you cannot remember doing.
  last_done_on  DATE,

  -- Kept rather than deleted when it stops applying, so the history below stays
  -- readable. A sold car's oil changes are still a true record.
  active        BOOLEAN NOT NULL DEFAULT TRUE,
  notes         TEXT NOT NULL DEFAULT '',
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_recurring_tasks_active ON recurring_tasks (active, last_done_on);

-- Every completion, not just the latest. "When did I last" is answered by
-- recurring_tasks.last_done_on; "how long have I actually been keeping this up" needs
-- the series, and once a completion is overwritten that answer is gone for good.
CREATE TABLE recurring_task_completions (
  id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  task_id    BIGINT NOT NULL REFERENCES recurring_tasks (id) ON DELETE CASCADE,
  done_on    DATE NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

  -- Tapping "did it" twice in one day is a double tap, not two completions.
  CONSTRAINT recurring_task_completions_one_per_day UNIQUE (task_id, done_on)
);
CREATE INDEX idx_recurring_task_completions_task ON recurring_task_completions (task_id, done_on DESC);

--rollback DROP TABLE recurring_task_completions;
--rollback DROP TABLE recurring_tasks;
--rollback DROP TABLE calendar_events;
