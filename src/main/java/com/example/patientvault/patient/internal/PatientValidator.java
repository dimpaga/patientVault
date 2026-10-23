package com.example.patientvault.patient.internal;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

/**
 * Server-side validation and normalisation of patient forms (authoritative; the browser only hints).
 * Produces message keys, never echoes values.
 */
@Component
public class PatientValidator {

    public static final int MAX_NAME = 100;
    public static final int MAX_EMAIL = 254;
    public static final int MAX_PHONE = 40;
    public static final int MAX_AMKA_INPUT = 20;
    public static final int MAX_REFERRAL = 500;
    public static final int MAX_TEXT = 10_000;

    /** One '@', no spaces, a dot in the domain; deliberately pragmatic rather than RFC-complete. */
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@.]+(\\.[^\\s@.]+)+$");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** Outcome of validating a whole form. {@code data} / {@code firstVisit} are only meaningful when valid. */
    public record Result(PatientData data, VisitData firstVisit, Map<String, FieldMessage> errors,
                         Map<String, FieldMessage> warnings) {
        public boolean valid() {
            return errors.isEmpty();
        }
    }

    /** Result for a single field: at most one error or warning. */
    public record FieldResult(FieldMessage error, FieldMessage warning) {
    }

    private final Validator validator;
    private final AmkaProperties amka;
    private final Clock clock;
    private final ZoneId zone;

    PatientValidator(Validator validator, AmkaProperties amka, Clock clock, @Value("${app.timezone}") ZoneId zone) {
        this.validator = validator;
        this.amka = amka;
        this.clock = clock;
        this.zone = zone;
    }

    public Result validate(PatientInput input, boolean includeFirstVisit) {
        Map<String, FieldMessage> errors = new LinkedHashMap<>();
        Map<String, FieldMessage> warnings = new LinkedHashMap<>();

        for (ConstraintViolation<PatientInput> violation : validator.validate(input)) {
            Object max = violation.getConstraintDescriptor().getAttributes().get("max");
            errors.putIfAbsent(violation.getPropertyPath().toString(), new FieldMessage("patient.error.tooLong", max));
        }

        String firstName = name(input.firstName());
        String lastName = name(input.lastName());
        String email = lower(input.email());
        String amkaDigits = Amka.normalize(input.amka());
        String mobile = null;

        check(errors, warnings, "firstName", requiredText(firstName));
        check(errors, warnings, "lastName", requiredText(lastName));
        check(errors, warnings, "email", emailCheck(email));
        FieldResult mobileResult = mobileCheck(input.mobile());
        check(errors, warnings, "mobile", mobileResult);
        if (mobileResult.error() == null) {
            mobile = PhoneNumbers.toE164(input.mobile()).orElse(null);
        }
        check(errors, warnings, "amka", amkaCheck(amkaDigits));

        VisitData visit = null;
        if (includeFirstVisit) {
            visit = firstVisit(input, errors);
        }

        if (!errors.isEmpty()) {
            return new Result(null, null, errors, warnings);
        }
        PatientData data = new PatientData(firstName, lastName, email, mobile, amkaDigits,
                optional(input.referralSource()), optionalMultiline(input.notes()), input.consentGiven());
        return new Result(data, visit, errors, warnings);
    }

    /** Outcome of validating one visit form. {@code data} is only meaningful when valid. */
    public record VisitResult(VisitData data, Map<String, FieldMessage> errors) {
        public boolean valid() {
            return errors.isEmpty();
        }
    }

    /** Validates one visit form (adding or editing a visit). */
    public VisitResult validateVisit(VisitInput input) {
        Map<String, FieldMessage> errors = new LinkedHashMap<>();
        if (input.visitDate() == null) {
            errors.put("visitDate", FieldMessage.of("patient.error.required"));
        } else if (input.visitDate().isAfter(today())) {
            errors.put("visitDate", FieldMessage.of("patient.error.date.future"));
        }
        for (ConstraintViolation<VisitInput> violation : validator.validate(input)) {
            Object max = violation.getConstraintDescriptor().getAttributes().get("max");
            errors.putIfAbsent(violation.getPropertyPath().toString(), new FieldMessage("patient.error.tooLong", max));
        }
        if (!errors.isEmpty()) {
            return new VisitResult(null, errors);
        }
        return new VisitResult(new VisitData(input.visitDate(), optionalMultiline(input.description()),
                optionalMultiline(input.diagnosis()), optionalMultiline(input.treatment())), errors);
    }

    /** Inline (on-blur) validation of one of the mandatory fields. */
    public Optional<FieldResult> validateField(String field, String value) {
        return switch (field) {
            case "firstName", "lastName" -> Optional.of(requiredText(name(value)));
            case "email" -> Optional.of(emailCheck(lower(value)));
            case "mobile" -> Optional.of(mobileCheck(value));
            case "amka" -> Optional.of(amkaCheck(Amka.normalize(value)));
            default -> Optional.empty();
        };
    }

    private VisitData firstVisit(PatientInput input, Map<String, FieldMessage> errors) {
        String description = optionalMultiline(input.visitDescription());
        String diagnosis = optionalMultiline(input.visitDiagnosis());
        String treatment = optionalMultiline(input.visitTreatment());
        boolean any = input.visitDate() != null || description != null || diagnosis != null || treatment != null;
        if (!any) {
            return null;
        }
        LocalDate date = input.visitDate() != null ? input.visitDate() : today();
        if (date.isAfter(today())) {
            errors.put("visitDate", FieldMessage.of("patient.error.date.future"));
            return null;
        }
        return new VisitData(date, description, diagnosis, treatment);
    }

    private static FieldResult requiredText(String value) {
        return value.isEmpty() ? error("patient.error.required") : ok();
    }

    private static FieldResult emailCheck(String email) {
        if (email.isEmpty()) {
            return error("patient.error.required");
        }
        return email.length() <= MAX_EMAIL && EMAIL.matcher(email).matches() ? ok() : error("patient.error.email.invalid");
    }

    private static FieldResult mobileCheck(String mobile) {
        if (mobile == null || mobile.isBlank()) {
            return error("patient.error.required");
        }
        return PhoneNumbers.toE164(mobile).isPresent() ? ok() : error("patient.error.mobile.invalid");
    }

    private FieldResult amkaCheck(String digits) {
        if (digits.isEmpty()) {
            return error("patient.error.required");
        }
        if (!Amka.hasValidLength(digits)) {
            return error("patient.error.amka.length");
        }
        if (!Amka.hasValidChecksum(digits)) {
            return error("patient.error.amka.checksum");
        }
        if (!Amka.hasParsableBirthDate(digits)) {
            return amka.strictDate() ? error("patient.error.amka.date") : new FieldResult(null, FieldMessage.of("patient.warning.amka.date"));
        }
        return ok();
    }

    private static void check(Map<String, FieldMessage> errors, Map<String, FieldMessage> warnings, String field,
                              FieldResult result) {
        if (result.error() != null) {
            errors.putIfAbsent(field, result.error());
        } else if (result.warning() != null) {
            warnings.put(field, result.warning());
        }
    }

    private static FieldResult ok() {
        return new FieldResult(null, null);
    }

    private static FieldResult error(String key) {
        return new FieldResult(FieldMessage.of(key), null);
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(zone));
    }

    private static String name(String value) {
        return value == null ? "" : WHITESPACE.matcher(value.strip()).replaceAll(" ");
    }

    private static String lower(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    private static String optional(String value) {
        String text = value == null ? "" : WHITESPACE.matcher(value.strip()).replaceAll(" ");
        return text.isEmpty() ? null : text;
    }

    private static String optionalMultiline(String value) {
        String text = value == null ? "" : value.strip();
        return text.isEmpty() ? null : text;
    }
}
