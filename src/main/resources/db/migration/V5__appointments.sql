-- Appointments and reminders (spec 5.2). Reason text is ciphertext.
CREATE TABLE appointment (
    id               uuid PRIMARY KEY,
    patient_id       uuid        NOT NULL REFERENCES patient (id) ON DELETE CASCADE,
    scheduled_at     timestamptz NOT NULL,
    duration_minutes int         NOT NULL DEFAULT 30,
    status           text        NOT NULL DEFAULT 'SCHEDULED',   -- SCHEDULED | COMPLETED | CANCELLED
    reason_enc       bytea,
    created_at       timestamptz NOT NULL,
    updated_at       timestamptz NOT NULL,
    version          bigint      NOT NULL DEFAULT 0
);
CREATE INDEX ix_appt_time ON appointment (scheduled_at) WHERE status = 'SCHEDULED';
CREATE INDEX ix_appt_patient ON appointment (patient_id, scheduled_at DESC);

CREATE TABLE reminder (
    id              uuid PRIMARY KEY,
    appointment_id  uuid        NOT NULL REFERENCES appointment (id) ON DELETE CASCADE,
    remind_at       timestamptz NOT NULL,
    status          text        NOT NULL DEFAULT 'PENDING',      -- PENDING | SENT | FAILED | EXPIRED | CANCELLED
    attempts        int         NOT NULL DEFAULT 0,
    next_attempt_at timestamptz,
    last_error      text,                                         -- technical error class only, no personal data
    sent_at         timestamptz
);
CREATE INDEX ix_reminder_due ON reminder (remind_at) WHERE status = 'PENDING';
CREATE INDEX ix_reminder_appointment ON reminder (appointment_id);

-- one row per local day on which the daily agenda e-mail was handled (prevents a second send after a restart)
CREATE TABLE digest_run (
    day     date PRIMARY KEY,
    sent_at timestamptz NOT NULL
);
