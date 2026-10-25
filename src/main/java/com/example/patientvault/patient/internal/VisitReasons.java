package com.example.patientvault.patient.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The reasons offered in the visit form, from {@code doctor_visit_reasons}. A visit stores only the (encrypted) code;
 * names are shown in the user's language (Greek, otherwise English). Inactive reasons are no longer offered but
 * still shown on visits that already use them.
 */
@Service
public class VisitReasons {

    /** One dropdown entry. */
    public record Option(String code, String label) {
    }

    private final VisitReasonRepository repository;

    VisitReasons(VisitReasonRepository repository) {
        this.repository = repository;
    }

    /** Active reasons in display order, named for the current locale. */
    @Transactional(readOnly = true)
    public List<Option> options() {
        return options(null);
    }

    /**
     * Like {@link #options()}, plus {@code keepCode} when that reason has since been deactivated, so that editing an
     * old visit does not silently clear its reason.
     */
    @Transactional(readOnly = true)
    public List<Option> options(String keepCode) {
        Locale locale = LocaleContextHolder.getLocale();
        List<VisitReasonEntity> reasons = repository.findByActiveTrueOrderBySortOrderAscCodeAsc();
        List<Option> options = new ArrayList<>(reasons.stream().map(r -> new Option(r.getCode(), name(r, locale))).toList());
        if (keepCode != null && !keepCode.isBlank() && options.stream().noneMatch(o -> o.code().equals(keepCode))) {
            repository.findById(keepCode).ifPresent(r -> options.add(new Option(r.getCode(), name(r, locale))));
        }
        return options;
    }

    /** Code to name for every reason, including inactive ones (for displaying saved visits). */
    @Transactional(readOnly = true)
    public Map<String, String> labels() {
        Locale locale = LocaleContextHolder.getLocale();
        Map<String, String> labels = new LinkedHashMap<>();
        repository.findAll().forEach(r -> labels.put(r.getCode(), name(r, locale)));
        return labels;
    }

    /** True for every reason in the table, active or not (an old visit may keep a deactivated reason). */
    @Transactional(readOnly = true)
    public boolean exists(String code) {
        return repository.existsById(code);
    }

    private static String name(VisitReasonEntity reason, Locale locale) {
        return "el".equals(locale.getLanguage()) ? reason.getNameEl() : reason.getNameEn();
    }
}
