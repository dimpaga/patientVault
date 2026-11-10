-- Postal address of the patient (optional free text, encrypted like the other personal details).
ALTER TABLE patient ADD COLUMN address_enc bytea;
