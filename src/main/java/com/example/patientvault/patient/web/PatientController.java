package com.example.patientvault.patient.web;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.patientvault.patient.internal.ConcurrentEditException;
import com.example.patientvault.patient.internal.DuplicateAmkaException;
import com.example.patientvault.patient.internal.FieldMessage;
import com.example.patientvault.patient.internal.PatientNotFoundException;
import com.example.patientvault.patient.internal.PatientService;
import com.example.patientvault.patient.internal.PatientValidator;
import com.example.patientvault.patient.internal.PatientView;
import com.example.patientvault.shared.Permissions;

/** Create, view, edit and inline-validate patients. */
@Controller
@RequestMapping("/patients")
class PatientController {

    private static final Set<String> INLINE_FIELDS = Set.of("firstName", "lastName", "email", "mobile", "amka");
    private static final Set<String> MORE_DETAILS_FIELDS = Set.of(
            "referralSource", "notes", "visitDate", "visitDescription", "visitDiagnosis", "visitTreatment");

    private final PatientService patients;
    private final PatientValidator validator;
    private final PatientPage page;

    PatientController(PatientService patients, PatientValidator validator, PatientPage page) {
        this.patients = patients;
        this.validator = validator;
        this.page = page;
    }

    // ---- create -------------------------------------------------------------------------------

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('" + Permissions.PATIENT_WRITE + "')")
    String newForm(Model model) {
        return form(model, PatientForm.empty(), null, Map.of(), Map.of(), false, null);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permissions.PATIENT_WRITE + "')")
    String create(PatientForm form, BindingResult binding, Model model, RedirectAttributes redirect) {
        PatientValidator.Result result = validator.validate(form.toInput(), true);
        Map<String, FieldMessage> errors = withBindingErrors(result.errors(), binding);
        if (!errors.isEmpty()) {
            return form(model, form, null, errors, result.warnings(), false, null);
        }
        try {
            PatientService.SaveResult saved = patients.create(result.data(), result.firstVisit());
            redirect.addFlashAttribute("flashSuccess", "patient.created");
            redirect.addFlashAttribute("flashWarnings", saved.warnings());
            return "redirect:/patients/" + saved.id();
        } catch (DuplicateAmkaException e) {
            return form(model, form, null, Map.of("amka", FieldMessage.of("patient.error.amka.duplicate")),
                    result.warnings(), false, e.getExistingPatientId());
        }
    }

    // ---- inline validation (hx-post on blur) --------------------------------------------------

    @PostMapping("/validate")
    @PreAuthorize("hasAuthority('" + Permissions.PATIENT_WRITE + "')")
    String validateField(@RequestParam Map<String, String> params,
                         @RequestParam(name = "patientId", required = false) UUID patientId,
                         @RequestHeader(name = "HX-Trigger-Name", required = false) String triggerName, Model model) {
        // htmx submits the whole enclosing form; HX-Trigger-Name tells which field was actually blurred
        String field = triggerName != null ? triggerName
                : params.keySet().stream().filter(INLINE_FIELDS::contains).findFirst().orElse(null);
        if (field == null || !INLINE_FIELDS.contains(field)) {
            return "fragments/patient :: feedbackContent(error=null, warning=null, duplicateOf=null)";
        }
        String value = params.get(field);
        var format = validator.validateField(field, value).orElse(null);
        FieldMessage error = format == null ? null : format.error();
        FieldMessage warning = format == null ? null : format.warning();
        UUID duplicateOf = null;
        if (error == null) {
            var unique = patients.checkUniqueness(field, value, patientId);
            error = unique.error();
            duplicateOf = unique.existingPatientId();
            if (unique.warning() != null) {
                warning = unique.warning();
            }
        }
        model.addAttribute("error", error);
        model.addAttribute("warning", warning);
        model.addAttribute("duplicateOf", duplicateOf);
        return "fragments/patient :: feedbackContent(error=${error}, warning=${warning}, duplicateOf=${duplicateOf})";
    }

    // ---- view ---------------------------------------------------------------------------------

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.PATIENT_READ + "')")
    String view(@PathVariable UUID id, @RequestParam(name = "tab", required = false) String tab,
                @RequestParam(name = "edit", required = false) UUID editVisit,
                @RequestParam(name = "folder", required = false) UUID folder,
                @RequestParam(name = "view", required = false) String fileView,
                @RequestHeader(name = "HX-Request", required = false) String htmx,
                @RequestHeader(name = "HX-Boosted", required = false) String boosted, Model model) {
        return render(id, tab, editVisit, folder, fileView, htmx, boosted, model);
    }

    /** "View medical data": the patient's page opened on the Files tab. */
    @GetMapping("/{id}/files")
    @PreAuthorize("hasAuthority('" + Permissions.PATIENT_READ + "')")
    String files(@PathVariable UUID id, @RequestParam(name = "folder", required = false) UUID folder,
                 @RequestParam(name = "view", required = false) String fileView,
                 @RequestHeader(name = "HX-Request", required = false) String htmx,
                 @RequestHeader(name = "HX-Boosted", required = false) String boosted, Model model) {
        return render(id, "files", null, folder, fileView, htmx, boosted, model);
    }

    private String render(UUID id, String tab, UUID editVisit, UUID folder, String fileView, String htmx,
                          String boosted, Model model) {
        PatientView patient = page.load(id);
        PatientPage.VisitState state = page.stateForEdit(patient.id(), editVisit);
        PatientPage.FilesState filesState = new PatientPage.FilesState(folder, "list".equals(fileView) ? "list" : "grid");
        page.fill(model, patient, tab, state, filesState);
        // htmx tab clicks swap only the tab bar and panel; boosted navigation and plain requests get the full page
        boolean tabSwap = "true".equalsIgnoreCase(htmx) && !"true".equalsIgnoreCase(boosted);
        return tabSwap ? "pages/patient-view :: tabs" : "pages/patient-view";
    }

    // ---- edit ---------------------------------------------------------------------------------

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('" + Permissions.PATIENT_WRITE + "')")
    String editForm(@PathVariable UUID id, Model model) {
        PatientView patient = patients.get(id).orElseThrow(PatientNotFoundException::new);
        return form(model, PatientForm.from(patient.data(), patient.version()), id, Map.of(), Map.of(), false, null);
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.PATIENT_WRITE + "')")
    String update(@PathVariable UUID id, PatientForm form, BindingResult binding, Model model,
                  RedirectAttributes redirect) {
        PatientValidator.Result result = validator.validate(form.toInput(), false);
        Map<String, FieldMessage> errors = withBindingErrors(result.errors(), binding);
        if (!errors.isEmpty()) {
            return form(model, form, id, errors, result.warnings(), false, null);
        }
        if (form.version() == null) {
            throw new PatientNotFoundException();
        }
        try {
            PatientService.SaveResult saved = patients.update(id, form.version(), result.data());
            redirect.addFlashAttribute("flashSuccess", "patient.updated");
            redirect.addFlashAttribute("flashWarnings", saved.warnings());
            return "redirect:/patients/" + id;
        } catch (DuplicateAmkaException e) {
            return form(model, form, id, Map.of("amka", FieldMessage.of("patient.error.amka.duplicate")),
                    result.warnings(), false, e.getExistingPatientId());
        } catch (ConcurrentEditException e) {
            return form(model, form, id, Map.of(), result.warnings(), true, null);
        }
    }

    // ---- helpers ------------------------------------------------------------------------------

    private String form(Model model, PatientForm form, UUID patientId, Map<String, FieldMessage> errors,
                        Map<String, FieldMessage> warnings, boolean conflict, UUID duplicateOf) {
        model.addAttribute("form", form);
        model.addAttribute("patientId", patientId);
        model.addAttribute("errors", errors);
        model.addAttribute("warnings", warnings);
        model.addAttribute("conflict", conflict);
        model.addAttribute("duplicateOf", duplicateOf);
        model.addAttribute("moreOpen", errors.keySet().stream().anyMatch(MORE_DETAILS_FIELDS::contains)
                || (patientId == null && hasMoreDetails(form)) || (patientId != null && hasMoreEdit(form)));
        model.addAttribute("nav", patientId == null ? "new" : "patients");
        return "pages/patient-form";
    }

    /** A value that could not even be converted (for example a malformed date) becomes a field error. */
    private static Map<String, FieldMessage> withBindingErrors(Map<String, FieldMessage> errors, BindingResult binding) {
        if (!binding.hasFieldErrors()) {
            return errors;
        }
        Map<String, FieldMessage> merged = new java.util.LinkedHashMap<>(errors);
        binding.getFieldErrors().forEach(e -> merged.putIfAbsent(e.getField(), FieldMessage.of("patient.error.value.invalid")));
        return merged;
    }

    private static boolean hasMoreDetails(PatientForm form) {
        return form.visitDate() != null || notBlank(form.visitDescription()) || notBlank(form.visitDiagnosis())
                || notBlank(form.visitTreatment()) || notBlank(form.referralSource()) || notBlank(form.notes())
                || Boolean.TRUE.equals(form.consentGiven());
    }

    private static boolean hasMoreEdit(PatientForm form) {
        return notBlank(form.referralSource()) || notBlank(form.notes()) || Boolean.TRUE.equals(form.consentGiven());
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
