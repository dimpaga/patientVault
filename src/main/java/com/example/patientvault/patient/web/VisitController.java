package com.example.patientvault.patient.web;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.patientvault.patient.internal.ConcurrentEditException;
import com.example.patientvault.patient.internal.FieldMessage;
import com.example.patientvault.patient.internal.PatientNotFoundException;
import com.example.patientvault.patient.internal.PatientService;
import com.example.patientvault.patient.internal.PatientValidator;
import com.example.patientvault.patient.internal.PatientView;
import com.example.patientvault.shared.Permissions;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Visits of a patient. Every route checks that the patient is ACTIVE and that the visit belongs to it
 * (done in {@link PatientService}). htmx requests receive the refreshed tab; plain form posts get a redirect.
 */
@Controller
@RequestMapping("/patients/{patientId}/visits")
class VisitController {

    private final PatientService patients;
    private final PatientValidator validator;
    private final PatientPage page;
    private final Toasts toasts;

    VisitController(PatientService patients, PatientValidator validator, PatientPage page, Toasts toasts) {
        this.patients = patients;
        this.validator = validator;
        this.page = page;
        this.toasts = toasts;
    }

    /** Inline edit form for one visit (htmx), or the full page with the visit open for editing (no JavaScript). */
    @GetMapping("/{visitId}/edit")
    @PreAuthorize("hasAuthority('" + Permissions.PATIENT_WRITE + "')")
    String editForm(@PathVariable UUID patientId, @PathVariable UUID visitId,
                    @RequestHeader(name = "HX-Request", required = false) String htmx, Model model) {
        PatientView patient = page.load(patientId);
        PatientPage.VisitState state = page.stateForEdit(patientId, visitId);
        if (state.editId() == null) {
            throw new PatientNotFoundException();
        }
        page.fill(model, patient, "visits", state);
        return "true".equalsIgnoreCase(htmx) ? "patients/visits :: visitEdit" : "pages/patient-view";
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permissions.PATIENT_WRITE + "')")
    String add(@PathVariable UUID patientId, VisitForm form, BindingResult binding,
               @RequestHeader(name = "HX-Request", required = false) String htmx, Model model,
               HttpServletResponse response, RedirectAttributes redirect) {
        PatientValidator.VisitResult result = validator.validateVisit(form.toInput());
        Map<String, FieldMessage> errors = withBindingErrors(result.errors(), binding);
        if (!errors.isEmpty()) {
            return renderTab(patientId, page.stateForEdit(patientId, null).withAddForm(form, errors), htmx, model);
        }
        patients.addVisit(patientId, result.data());
        return done(patientId, "visit.added", htmx, model, response, redirect);
    }

    @PostMapping("/{visitId}")
    @PreAuthorize("hasAuthority('" + Permissions.PATIENT_WRITE + "')")
    String update(@PathVariable UUID patientId, @PathVariable UUID visitId, VisitForm form, BindingResult binding,
                  @RequestHeader(name = "HX-Request", required = false) String htmx, Model model,
                  HttpServletResponse response, RedirectAttributes redirect) {
        PatientValidator.VisitResult result = validator.validateVisit(form.toInput());
        Map<String, FieldMessage> errors = withBindingErrors(result.errors(), binding);
        PatientPage.VisitState base = page.stateForEdit(patientId, null);
        if (!errors.isEmpty()) {
            return renderTab(patientId, base.editing(visitId, form, errors, false), htmx, model);
        }
        if (form.version() == null) {
            throw new PatientNotFoundException();
        }
        try {
            patients.updateVisit(patientId, visitId, form.version(), result.data());
        } catch (ConcurrentEditException e) {
            return renderTab(patientId, base.editing(visitId, form, Map.of(), true), htmx, model);
        }
        return done(patientId, "visit.updated", htmx, model, response, redirect);
    }

    @PostMapping("/{visitId}/delete")
    @PreAuthorize("hasAuthority('" + Permissions.PATIENT_WRITE + "')")
    String delete(@PathVariable UUID patientId, @PathVariable UUID visitId,
                  @RequestHeader(name = "HX-Request", required = false) String htmx, Model model,
                  HttpServletResponse response, RedirectAttributes redirect) {
        patients.deleteVisit(patientId, visitId);
        return done(patientId, "visit.deleted", htmx, model, response, redirect);
    }

    // ---- helpers ------------------------------------------------------------------------------

    /** After a successful change: htmx gets the refreshed tab plus a toast, others a redirect with a flash message. */
    private String done(UUID patientId, String messageKey, String htmx, Model model, HttpServletResponse response,
                        RedirectAttributes redirect) {
        if ("true".equalsIgnoreCase(htmx)) {
            HttpHeaders headers = toasts.success(messageKey);
            headers.forEach((name, values) -> values.forEach(value -> response.addHeader(name, value)));
            return renderTab(patientId, page.stateForEdit(patientId, null), htmx, model);
        }
        redirect.addFlashAttribute("flashSuccess", messageKey);
        return "redirect:/patients/" + patientId + "?tab=visits";
    }

    private String renderTab(UUID patientId, PatientPage.VisitState state, String htmx, Model model) {
        page.fill(model, page.load(patientId), "visits", state);
        return "true".equalsIgnoreCase(htmx) ? "pages/patient-view :: tabs" : "pages/patient-view";
    }

    private static Map<String, FieldMessage> withBindingErrors(Map<String, FieldMessage> errors, BindingResult binding) {
        if (!binding.hasFieldErrors()) {
            return errors;
        }
        Map<String, FieldMessage> merged = new LinkedHashMap<>(errors);
        binding.getFieldErrors().forEach(e -> merged.putIfAbsent(e.getField(), FieldMessage.of("patient.error.value.invalid")));
        return merged;
    }
}
