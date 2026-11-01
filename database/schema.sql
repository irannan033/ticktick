-- Target cloud schema (PostgreSQL) for Phase 3/4. Mirrors the Android Room schema
-- (android-app/app/schemas) so the same records can sync both ways.
-- Primary keys are client-generated UUIDs so offline-created rows never collide.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TYPE task_priority AS ENUM ('HIGH', 'MEDIUM', 'LOW', 'NONE');
-- OVERDUE is derived (status open AND due_at < now) and never stored.
CREATE TYPE task_status   AS ENUM ('UPCOMING', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED');

CREATE TABLE users (
    user_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    google_sub       TEXT UNIQUE,                -- Google/Firebase subject id
    name             TEXT NOT NULL,
    email            TEXT UNIQUE,
    phone_number     TEXT,
    whatsapp_number  TEXT UNIQUE,                -- E.164, e.g. +8801712345678
    timezone         TEXT NOT NULL DEFAULT 'UTC',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE categories (
    category_id  UUID PRIMARY KEY,
    user_id      UUID NOT NULL REFERENCES users ON DELETE CASCADE,
    name         TEXT NOT NULL,
    color_argb   BIGINT NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at   TIMESTAMPTZ                      -- tombstone for sync
);

CREATE TABLE tasks (
    task_id            UUID PRIMARY KEY,
    user_id            UUID NOT NULL REFERENCES users ON DELETE CASCADE,
    title              TEXT NOT NULL,
    description        TEXT NOT NULL DEFAULT '',
    priority           task_priority NOT NULL DEFAULT 'NONE',
    important          BOOLEAN NOT NULL DEFAULT FALSE,
    urgent             BOOLEAN NOT NULL DEFAULT FALSE,
    category_id        UUID REFERENCES categories ON DELETE SET NULL,
    start_datetime     TIMESTAMPTZ,
    due_datetime       TIMESTAMPTZ,
    reminder_datetime  TIMESTAMPTZ,
    estimated_minutes  INT,
    status             task_status NOT NULL DEFAULT 'UPCOMING',
    completed_at       TIMESTAMPTZ,
    postpone_count     INT NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,      -- last-write-wins conflict resolution
    deleted_at         TIMESTAMPTZ
);
CREATE INDEX tasks_user_due ON tasks (user_id, due_datetime);
CREATE INDEX tasks_user_status ON tasks (user_id, status);

CREATE TABLE task_activity (
    activity_id      BIGSERIAL PRIMARY KEY,
    task_id          UUID NOT NULL REFERENCES tasks ON DELETE CASCADE,
    action           TEXT NOT NULL,
    previous_status  task_status,
    new_status       task_status,
    note             TEXT,
    timestamp        TIMESTAMPTZ NOT NULL
);

-- Phase 2
CREATE TABLE pomodoro_sessions (
    session_id          UUID PRIMARY KEY,
    task_id             UUID REFERENCES tasks ON DELETE SET NULL,
    user_id             UUID NOT NULL REFERENCES users ON DELETE CASCADE,
    duration_minutes    INT NOT NULL,
    completed_seconds   INT NOT NULL DEFAULT 0,
    started_at          TIMESTAMPTZ NOT NULL,
    ended_at            TIMESTAMPTZ,
    status              TEXT NOT NULL CHECK (status IN ('RUNNING','COMPLETED','ABANDONED'))
);

CREATE TABLE notification_settings (
    user_id               UUID PRIMARY KEY REFERENCES users ON DELETE CASCADE,
    notification_enabled  BOOLEAN NOT NULL DEFAULT TRUE,
    whatsapp_enabled      BOOLEAN NOT NULL DEFAULT FALSE,
    morning_reminder_time TIME NOT NULL DEFAULT '08:00',
    followup_interval_h   INT  NOT NULL DEFAULT 2,
    evening_report_time   TIME NOT NULL DEFAULT '23:00',
    paused_until          TIMESTAMPTZ
);

-- Phase 4: message log. UNIQUE whatsapp_message_id makes webhook retries idempotent.
CREATE TABLE whatsapp_messages (
    message_id           BIGSERIAL PRIMARY KEY,
    user_id              UUID NOT NULL REFERENCES users ON DELETE CASCADE,
    direction            TEXT NOT NULL CHECK (direction IN ('IN','OUT')),
    message_content      TEXT NOT NULL,
    message_status       TEXT NOT NULL,          -- queued/sent/delivered/read/failed
    whatsapp_message_id  TEXT UNIQUE,
    retry_count          INT NOT NULL DEFAULT 0,
    timestamp            TIMESTAMPTZ NOT NULL DEFAULT now()
);
