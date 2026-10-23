-- Medical files and folders (spec 5.2). Only metadata; content lives (encrypted) in CouchDB.
CREATE TABLE medical_folder (
    id         uuid PRIMARY KEY,
    patient_id uuid        NOT NULL REFERENCES patient (id) ON DELETE CASCADE,
    parent_id  uuid        REFERENCES medical_folder (id) ON DELETE CASCADE,   -- NULL = root
    name_enc   bytea       NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);
CREATE INDEX ix_folder_patient_parent ON medical_folder (patient_id, parent_id);

CREATE TABLE medical_file (
    id                uuid PRIMARY KEY,
    patient_id        uuid        NOT NULL REFERENCES patient (id) ON DELETE CASCADE,
    folder_id         uuid        REFERENCES medical_folder (id) ON DELETE CASCADE,
    visit_id          uuid        REFERENCES visit (id) ON DELETE SET NULL,
    category          text        NOT NULL,                  -- PHOTO | PDF | DICOM | OTHER
    display_name_enc  bytea       NOT NULL,                  -- original file name (may contain personal data)
    mime_type         text        NOT NULL,                  -- detected server-side
    size_bytes        bigint      NOT NULL,                  -- plaintext size
    ciphertext_sha256 bytea,                                 -- integrity check of the stored blob
    couch_doc_id      text        NOT NULL UNIQUE,
    has_thumbnail     boolean     NOT NULL DEFAULT false,
    status            text        NOT NULL DEFAULT 'PENDING', -- PENDING | AVAILABLE | DELETING
    uploaded_at       timestamptz NOT NULL,
    updated_at        timestamptz NOT NULL
);
CREATE INDEX ix_file_patient ON medical_file (patient_id, folder_id);
CREATE INDEX ix_file_status ON medical_file (status, uploaded_at);
