-- Identity: the single doctor account and MFA recovery codes (spec 5.2).
CREATE TABLE app_user (
    id                     uuid PRIMARY KEY,
    username               text        NOT NULL UNIQUE,
    password_hash          text        NOT NULL,
    notification_email_enc bytea,
    totp_secret_enc        bytea,
    totp_enabled           boolean     NOT NULL DEFAULT false,
    totp_last_step         bigint,                         -- last accepted TOTP time step, blocks code replay
    must_change_password   boolean     NOT NULL DEFAULT true,
    failed_attempts        int         NOT NULL DEFAULT 0,
    locked_until           timestamptz,
    last_login_at          timestamptz,
    created_at             timestamptz NOT NULL,
    updated_at             timestamptz NOT NULL,
    version                bigint      NOT NULL DEFAULT 0
);

CREATE TABLE recovery_code (
    id        uuid PRIMARY KEY,
    user_id   uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    code_hash text NOT NULL,
    used_at   timestamptz
);
CREATE INDEX ix_recovery_code_user ON recovery_code (user_id);
