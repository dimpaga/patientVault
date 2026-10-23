package com.example.patientvault.patient.internal;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.example.patientvault.crypto.CryptoService;
import com.example.patientvault.crypto.DataKey;
import com.example.patientvault.crypto.NewDataKey;

/**
 * The only class that converts between ciphertext entities and plaintext {@link PatientData} /
 * {@link VisitData}. Fields are encrypted with the patient's data key; the associated data binds each
 * value to the patient and the field (visit fields additionally to the visit id).
 */
@Component
public class PatientCodec {

    static final String FIRST_NAME = "first_name";
    static final String LAST_NAME = "last_name";
    static final String EMAIL = "email";
    static final String MOBILE = "mobile";
    static final String AMKA = "amka";
    static final String REFERRAL_SOURCE = "referral_source";
    static final String NOTES = "notes";

    private final CryptoService crypto;

    PatientCodec(CryptoService crypto) {
        this.crypto = crypto;
    }

    /** Creates a new entity with a fresh data key and all fields encrypted. */
    PatientEntity newEntity(UUID id, PatientData data) {
        NewDataKey created = crypto.createDataKey(id);
        PatientEntity entity = new PatientEntity(id, created.wrapped().bytes(), created.wrapped().keyId());
        encryptInto(entity, created.key(), data);
        return entity;
    }

    /** Re-encrypts all fields of an existing entity with its own data key. */
    void update(PatientEntity entity, PatientData data) {
        encryptInto(entity, key(entity), data);
    }

    PatientData decode(PatientEntity entity) {
        DataKey key = key(entity);
        return new PatientData(
                text(key, FIRST_NAME, entity.getFirstNameEnc()),
                text(key, LAST_NAME, entity.getLastNameEnc()),
                text(key, EMAIL, entity.getEmailEnc()),
                text(key, MOBILE, entity.getMobileEnc()),
                text(key, AMKA, entity.getAmkaEnc()),
                text(key, REFERRAL_SOURCE, entity.getReferralSourceEnc()),
                text(key, NOTES, entity.getNotesEnc()),
                entity.isConsentGiven());
    }

    /** Names only, for lists that must not decrypt every field. */
    String[] decodeName(PatientEntity entity) {
        DataKey key = key(entity);
        return new String[] {text(key, FIRST_NAME, entity.getFirstNameEnc()), text(key, LAST_NAME, entity.getLastNameEnc())};
    }

    VisitEntity newVisit(PatientEntity patient, VisitData data) {
        VisitEntity visit = new VisitEntity(patient.getId());
        encryptInto(visit, key(patient), data);
        return visit;
    }

    void update(VisitEntity visit, PatientEntity patient, VisitData data) {
        encryptInto(visit, key(patient), data);
    }

    VisitData decode(VisitEntity visit, PatientEntity patient) {
        DataKey key = key(patient);
        return new VisitData(
                visit.getVisitDate(),
                text(key, visitField(visit, "description"), visit.getDescriptionEnc()),
                text(key, visitField(visit, "diagnosis"), visit.getDiagnosisEnc()),
                text(key, visitField(visit, "treatment"), visit.getTreatmentEnc()));
    }

    private DataKey key(PatientEntity entity) {
        return crypto.unwrapDataKey(entity.getId(), entity.getDekWrapped());
    }

    private void encryptInto(PatientEntity entity, DataKey key, PatientData data) {
        entity.setFirstNameEnc(crypto.encryptText(key, FIRST_NAME, data.firstName()));
        entity.setLastNameEnc(crypto.encryptText(key, LAST_NAME, data.lastName()));
        entity.setEmailEnc(crypto.encryptText(key, EMAIL, data.email()));
        entity.setMobileEnc(crypto.encryptText(key, MOBILE, data.mobile()));
        entity.setAmkaEnc(crypto.encryptText(key, AMKA, data.amka()));
        entity.setReferralSourceEnc(optional(key, REFERRAL_SOURCE, data.referralSource()));
        entity.setNotesEnc(optional(key, NOTES, data.notes()));
        entity.setConsentGiven(data.consentGiven());
    }

    private void encryptInto(VisitEntity visit, DataKey key, VisitData data) {
        visit.setVisitDate(data.visitDate());
        visit.setDescriptionEnc(optional(key, visitField(visit, "description"), data.description()));
        visit.setDiagnosisEnc(optional(key, visitField(visit, "diagnosis"), data.diagnosis()));
        visit.setTreatmentEnc(optional(key, visitField(visit, "treatment"), data.treatment()));
    }

    /** Visit text is bound to the visit as well, so a value cannot be moved to another visit of the same patient. */
    private static String visitField(VisitEntity visit, String field) {
        return "visit:" + visit.getId() + ":" + field;
    }

    private byte[] optional(DataKey key, String field, String value) {
        return value == null || value.isEmpty() ? null : crypto.encryptText(key, field, value);
    }

    private String text(DataKey key, String field, byte[] ciphertext) {
        return ciphertext == null ? null : crypto.decryptText(key, field, ciphertext);
    }
}
