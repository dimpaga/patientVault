-- File content moves from CouchDB to Amazon S3 (spec v2, ADR-001). File metadata stays here, in PostgreSQL.
-- storage_key / thumbnail_key are opaque S3 object keys (UUIDs only), see StorageKeys.
ALTER TABLE medical_file
    ADD COLUMN storage_key           text,
    ADD COLUMN thumbnail_key         text,            -- NULL when the file has no thumbnail
    ADD COLUMN ciphertext_size_bytes bigint;          -- size of the stored encrypted object

UPDATE medical_file
SET storage_key   = 'p/' || patient_id || '/f/' || id || '/content',
    thumbnail_key = CASE WHEN has_thumbnail THEN 'p/' || patient_id || '/f/' || id || '/thumb' END;

ALTER TABLE medical_file
    ALTER COLUMN storage_key SET NOT NULL,
    ADD CONSTRAINT ux_medical_file_storage_key UNIQUE (storage_key),
    DROP COLUMN couch_doc_id,
    DROP COLUMN has_thumbnail;
