-- Reasons for a visit offered in the visit form's dropdown (orthopaedic starter list).
-- A visit stores only the encrypted reason code; the readable names live here, in Greek and English.
CREATE TABLE doctor_visit_reasons (
    code        varchar(40)  PRIMARY KEY,
    name_el     varchar(100) NOT NULL,
    name_en     varchar(100) NOT NULL,
    sort_order  integer      NOT NULL,
    active      boolean      NOT NULL DEFAULT true,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now()
);

INSERT INTO doctor_visit_reasons (code, name_el, name_en, sort_order) VALUES
    ('NECK',       'Αυχένας',           'Neck',            10),
    ('SHOULDER',   'Ώμος',              'Shoulder',        20),
    ('UPPER_ARM',  'Βραχίονας',         'Upper arm',       30),
    ('ELBOW',      'Αγκώνας',           'Elbow',           40),
    ('WRIST',      'Καρπός',            'Wrist',           50),
    ('FINGERS',    'Δάκτυλα',           'Fingers',         60),
    ('LOWER_BACK', 'Μέση',              'Lower back',      70),
    ('BACK',       'Πλάτη',             'Back',            80),
    ('SPINE',      'Σπονδυλική στήλη',  'Spine',           90),
    ('HIP',        'Ισχίο',             'Hip',            100),
    ('THIGH',      'Μηρός',             'Thigh',          110),
    ('KNEE',       'Γόνατο',            'Knee',           120),
    ('ANKLE',      'Ποδοκνημική',       'Ankle',          130),
    ('FOOT',       'Πέλμα',             'Foot',           140);

-- The free-text description is replaced by the chosen reason. The old description ciphertext was bound to the
-- field name "description", so it cannot be reinterpreted as a reason code and is dropped (decision D-059).
ALTER TABLE visit DROP COLUMN description_enc;
ALTER TABLE visit ADD COLUMN reason_enc bytea;
