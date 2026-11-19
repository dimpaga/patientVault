-- Add appointment type field (same as visit type)
ALTER TABLE appointment ADD COLUMN appointment_type text NOT NULL DEFAULT 'DOCTOR_VISIT'; -- DOCTOR_VISIT | SURGERY

