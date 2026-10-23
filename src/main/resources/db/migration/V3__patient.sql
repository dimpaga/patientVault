-- Patients, visits and blind-index search tokens (spec 5.2). Columns ending in _enc are ciphertext.
CREATE TABLE patient (
    id                  uuid PRIMARY KEY,
    status              text        NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE | DELETING (hidden, erasure in progress)
    dek_wrapped         bytea       NOT NULL,                    -- per-patient data key, wrapped by the KEK
    key_id              text        NOT NULL,                    -- key ring version used to wrap the DEK
    first_name_enc      bytea       NOT NULL,
    last_name_enc       bytea       NOT NULL,
    email_enc           bytea       NOT NULL,
    mobile_enc          bytea       NOT NULL,
    amka_enc            bytea       NOT NULL,
    referral_source_enc bytea,
    notes_enc           bytea,
    consent_given       boolean     NOT NULL DEFAULT false,
    consent_recorded_at timestamptz,
    created_at          timestamptz NOT NULL,
    updated_at          timestamptz NOT NULL,
    version             bigint      NOT NULL DEFAULT 0
);
CREATE INDEX ix_patient_status_updated ON patient (status, updated_at DESC);

CREATE TABLE patient_search_token (
    patient_id uuid  NOT NULL REFERENCES patient (id) ON DELETE CASCADE,
    kind       text  NOT NULL,                                   -- NAME_PREFIX | EMAIL | MOBILE | AMKA
    token      bytea NOT NULL,                                   -- HMAC-SHA256 output
    PRIMARY KEY (patient_id, kind, token)
);
CREATE INDEX ix_pst_kind_token ON patient_search_token (kind, token);
-- one patient per AMKA, enforced on the blind index so the database never sees the number
CREATE UNIQUE INDEX ux_pst_amka ON patient_search_token (token) WHERE kind = 'AMKA';

CREATE TABLE visit (
    id            uuid PRIMARY KEY,
    patient_id    uuid        NOT NULL REFERENCES patient (id) ON DELETE CASCADE,
    visit_date    date        NOT NULL,
    description_enc bytea,
    diagnosis_enc   bytea,
    treatment_enc   bytea,
    created_at    timestamptz NOT NULL,
    updated_at    timestamptz NOT NULL,
    version       bigint      NOT NULL DEFAULT 0
);
CREATE INDEX ix_visit_patient_date ON visit (patient_id, visit_date DESC);
