-- Visit type: a doctor visit or a surgery. The column default marks every existing visit as a doctor visit.
ALTER TABLE visit
    ADD COLUMN visit_type varchar(20) NOT NULL DEFAULT 'DOCTOR_VISIT'
        CONSTRAINT ck_visit_type CHECK (visit_type IN ('DOCTOR_VISIT', 'SURGERY'));
