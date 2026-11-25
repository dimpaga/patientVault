package com.example.patientvault.appointment.web;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.patientvault.appointment.internal.AppointmentConflictException;
import com.example.patientvault.appointment.internal.AppointmentNotFoundException;
import com.example.patientvault.appointment.internal.AppointmentService;
import com.example.patientvault.appointment.internal.AppointmentValidationException;
import com.example.patientvault.appointment.internal.AppointmentView;
import com.example.patientvault.patient.PatientApi;
import com.example.patientvault.patient.PatientSummary;
import com.example.patientvault.shared.Permissions;

import jakarta.servlet.http.HttpServletResponse;

/**
 * The Appointments tab of a patient (htmx swaps {@code #pv-tabs}; plain posts redirect), the agenda page and the
 * deep link used in reminder e-mails. Patient and appointment ownership are checked by {@link AppointmentService}.
 */
@Controller
class AppointmentController {

    /** What the tab shows besides the lists: the add form and, optionally, one appointment in edit mode. */
    record TabState(AppointmentForm addForm, Map<String, String> addErrors, UUID editId, AppointmentForm editForm,
                    Map<String, String> editErrors, boolean editConflict, boolean focusAdd) {

        static TabState initial() {
            return new TabState(AppointmentForm.empty(), Map.of(), null, null, Map.of(), false, false);
        }
    }

    private final AppointmentService appointments;
    private final PatientApi patients;

    private final AppointmentToasts toasts;

    AppointmentController(AppointmentService appointments, PatientApi patients, AppointmentToasts toasts) {
        this.appointments = appointments;
        this.patients = patients;
        this.toasts = toasts;
    }

    @GetMapping("/patients/{patientId}/appointments")
    @PreAuthorize("hasAuthority('" + Permissions.PATIENT_READ + "')")
    String tab(@PathVariable UUID patientId, @RequestParam(name = "edit", required = false) UUID editId,
               @RequestParam(name = "new", required = false) String openNew,
               @RequestHeader(name = "HX-Request", required = false) String htmx, Model model) {
        TabState state = TabState.initial();
        if (editId != null) {
            AppointmentView view = appointments.get(patientId, editId);
            if (!view.scheduledStatus()) {
                throw new AppointmentNotFoundException();
            }
            state = new TabState(state.addForm(), Map.of(), editId, AppointmentForm.from(view), Map.of(), false, false);
        } else if (openNew != null) {
            state = new TabState(state.addForm(), Map.of(), null, null, Map.of(), false, true);
        }
        return render(patientId, state, htmx, model);
    }

    @PostMapping("/patients/{patientId}/appointments")
    @PreAuthorize("hasAuthority('" + Permissions.PATIENT_WRITE + "')")
    String create(@PathVariable UUID patientId, AppointmentForm form,
                  @RequestHeader(name = "HX-Request", required = false) String htmx, Model model,
                  HttpServletResponse response, RedirectAttributes redirect) {
        try {
            appointments.create(patientId, form.parsedScheduledAt(), form.parsedDuration(), form.reason(), form.appointmentType());
        } catch (AppointmentValidationException e) {
            TabState state = new TabState(form, e.errors(), null, null, Map.of(), false, true);
            return render(patientId, state, htmx, model);
        }
        return done(patientId, "appointment.created", htmx, null, null, model, response, redirect);
    }

    @PostMapping("/patients/{patientId}/appointments/{appointmentId}")
    @PreAuthorize("hasAuthority('" + Permissions.PATIENT_WRITE + "')")
    String update(@PathVariable UUID patientId, @PathVariable UUID appointmentId, AppointmentForm form,
                  @RequestHeader(name = "HX-Request", required = false) String htmx,
                  @RequestParam(name = "from", required = false) String fromLocation,
                  @RequestParam(name = "range", required = false) String rangeParam,
                  Model model, HttpServletResponse response, RedirectAttributes redirect) {
        if (form.version() == null) {
            throw new AppointmentNotFoundException();
        }
        try {
            appointments.reschedule(patientId, appointmentId, form.version(), form.parsedScheduledAt(),
                    form.parsedDuration(), form.reason());
        } catch (AppointmentValidationException e) {
            if ("agenda".equals(fromLocation)) {
                addAgendaModel(model, rangeParam);
                model.addAttribute("editId", appointmentId);
                model.addAttribute("editForm", form);
                model.addAttribute("editErrors", e.errors());
                return "appointments/agenda :: agenda";
            }
            // Request came from patient appointments tab: return tab with errors
            TabState base = TabState.initial();
            return render(patientId, new TabState(base.addForm(), Map.of(), appointmentId, form, e.errors(), false, false),
                    htmx, model);
        } catch (AppointmentConflictException e) {
            if ("agenda".equals(fromLocation)) {
                addAgendaModel(model, rangeParam);
                model.addAttribute("editId", appointmentId);
                model.addAttribute("editForm", form);
                model.addAttribute("editErrors", Map.of());
                return "appointments/agenda :: agenda";
            }
            // Request came from patient appointments tab: return tab with conflict flag
            TabState base = TabState.initial();
            return render(patientId, new TabState(base.addForm(), Map.of(), appointmentId, form, Map.of(), true, false),
                    htmx, model);
        }
        return done(patientId, "appointment.updated", htmx, fromLocation, rangeParam, model, response, redirect);
    }

    @PostMapping("/patients/{patientId}/appointments/{appointmentId}/cancel")
    @PreAuthorize("hasAuthority('" + Permissions.PATIENT_WRITE + "')")
    String cancel(@PathVariable UUID patientId, @PathVariable UUID appointmentId,
                  @RequestHeader(name = "HX-Request", required = false) String htmx,
                  @RequestParam(name = "from", required = false) String fromLocation,
                  @RequestParam(name = "range", required = false) String rangeParam,
                  Model model, HttpServletResponse response, RedirectAttributes redirect) {
        appointments.cancel(patientId, appointmentId);
        return done(patientId, "appointment.cancelled", htmx, fromLocation, rangeParam, model, response, redirect);
    }

    @PostMapping("/patients/{patientId}/appointments/{appointmentId}/complete")
    @PreAuthorize("hasAuthority('" + Permissions.PATIENT_WRITE + "')")
    String complete(@PathVariable UUID patientId, @PathVariable UUID appointmentId,
                    @RequestHeader(name = "HX-Request", required = false) String htmx,
                    @RequestParam(name = "from", required = false) String fromLocation,
                    @RequestParam(name = "range", required = false) String rangeParam,
                    Model model, HttpServletResponse response, RedirectAttributes redirect) {
        appointments.complete(patientId, appointmentId);
        return done(patientId, "appointment.completed", htmx, fromLocation, rangeParam, model, response, redirect);
    }

    /** Link in reminder e-mails: after login, opens the patient's Appointments tab. */
    @GetMapping("/appointments/{appointmentId}")
    @PreAuthorize("hasAuthority('" + Permissions.PATIENT_READ + "')")
    String deepLink(@PathVariable UUID appointmentId) {
        return "redirect:/patients/" + appointments.patientIdOf(appointmentId) + "/appointments";
    }

    /** Agenda: today, the next 7 days, the current month, or past appointments still open; grouped by day. */
    @GetMapping("/appointments")
    @PreAuthorize("hasAuthority('" + Permissions.PATIENT_READ + "')")
    String agenda(@RequestParam(name = "range", defaultValue = "today") String range,
                  @RequestParam(name = "edit", required = false) UUID editId,
                  @RequestHeader(name = "HX-Request", required = false) String htmx,
                  @RequestHeader(name = "HX-Boosted", required = false) String boosted, Model model) {
        addAgendaModel(model, range);
        model.addAttribute("nav", "appointments");

        // If editing an appointment, load the edit form
        if (editId != null) {
            UUID patientId = appointments.patientIdOf(editId);
            AppointmentView view = appointments.get(patientId, editId);
            if (!view.scheduledStatus()) {
                throw new AppointmentNotFoundException();
            }
            model.addAttribute("editId", editId);
            model.addAttribute("editForm", AppointmentForm.from(view));
            model.addAttribute("editErrors", Map.of());
        }

        // switching the time window asks for the list only (htmx); a normal or boosted navigation gets the whole page
        boolean listOnly = isHtmx(htmx) && !isHtmx(boosted);
        return listOnly ? "appointments/agenda :: agenda" : "pages/appointments";
    }

    // ---- helpers ------------------------------------------------------------------------------

    /** Fills the model for the agenda list of the given window: week, month, past (overdue and still open); anything else is today. */
    private void addAgendaModel(Model model, String range) {
        LocalDate today = LocalDate.now(appointments.zone());
        String selected = "week".equals(range) || "month".equals(range) || "past".equals(range) ? range : "today";
        List<AppointmentView> views;
        if ("past".equals(selected)) {
            views = appointments.pastOpen();
        } else {
            LocalDate from = "month".equals(selected) ? today.withDayOfMonth(1) : today;
            LocalDate to = switch (selected) {
                case "week" -> today.plusDays(7);
                case "month" -> today.withDayOfMonth(1).plusMonths(1);
                default -> today.plusDays(1);
            };
            views = appointments.scheduledBetween(from, to);
        }
        Map<LocalDate, List<AppointmentView>> days = new LinkedHashMap<>();
        for (AppointmentView view : views) {
            days.computeIfAbsent(view.scheduled().toLocalDate(), d -> new java.util.ArrayList<>()).add(view);
        }
        model.addAttribute("range", selected);
        model.addAttribute("days", days);
        model.addAttribute("today", today);
        model.addAttribute("now", java.time.LocalDateTime.now(appointments.zone()));
    }

    /** After a successful change: responses vary based on context (agenda or patient tab). */
    private String done(UUID patientId, String messageKey, String htmx, String fromLocation, String range, Model model,
                        HttpServletResponse response, RedirectAttributes redirect) {
        if (isHtmx(htmx)) {
            response.addHeader("HX-Trigger", toasts.success(messageKey));
            if ("agenda".equals(fromLocation)) {
                addAgendaModel(model, range);
                return "appointments/agenda :: agenda";
            } else {
                // Request came from patient appointments tab: return the tabs
                return render(patientId, TabState.initial(), htmx, model);
            }
        }
        redirect.addFlashAttribute("flashSuccess", messageKey);
        return "redirect:/patients/" + patientId + "/appointments";
    }

    private String render(UUID patientId, TabState state, String htmx, Model model) {
        PatientSummary patient = patients.findActive(patientId).orElseThrow(AppointmentNotFoundException::new);
        List<AppointmentView> all = appointments.forPatient(patientId);
        model.addAttribute("patient", patient);
        model.addAttribute("nav", "patients");
        model.addAttribute("tab", "appointments");
        model.addAttribute("upcoming", all.stream().filter(AppointmentView::scheduledStatus)
                .sorted(java.util.Comparator.comparing(AppointmentView::scheduled)).toList());
        model.addAttribute("history", all.stream().filter(v -> !v.scheduledStatus()).toList());
        model.addAttribute("now", java.time.LocalDateTime.now(appointments.zone()));
        model.addAttribute("addForm", state.addForm());
        model.addAttribute("addErrors", state.addErrors());
        model.addAttribute("focusAdd", state.focusAdd());
        model.addAttribute("editId", state.editId());
        model.addAttribute("editForm", state.editForm());
        model.addAttribute("editErrors", state.editErrors());
        model.addAttribute("editConflict", state.editConflict());
        return isHtmx(htmx) ? "pages/patient-view :: tabs" : "pages/patient-appointments";
    }

    private static boolean isHtmx(String header) {
        return "true".equalsIgnoreCase(header);
    }
}
