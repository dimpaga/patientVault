package com.example.patientvault.patient.web;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import com.example.patientvault.patient.internal.FieldMessage;
import com.example.patientvault.patient.internal.PatientNotFoundException;
import com.example.patientvault.patient.internal.PatientService;
import com.example.patientvault.patient.internal.PatientView;
import com.example.patientvault.records.RecordsApi;

/** Fills the model of the patient page (header, tab bar and the selected tab). Shared by the page and tab endpoints. */
@Component
class PatientPage {

    /** What the Visits tab shows besides the list: the add form and, optionally, one visit in edit mode. */
    record VisitState(VisitForm addForm, Map<String, FieldMessage> addErrors, UUID editId, VisitForm editForm,
                      Map<String, FieldMessage> editErrors, boolean editConflict) {

        static VisitState initial(LocalDate today) {
            return new VisitState(VisitForm.empty(today), Map.of(), null, null, Map.of(), false);
        }

        VisitState withAddForm(VisitForm form, Map<String, FieldMessage> errors) {
            return new VisitState(form, errors, editId, editForm, editErrors, editConflict);
        }

        VisitState editing(UUID visitId, VisitForm form, Map<String, FieldMessage> errors, boolean conflict) {
            return new VisitState(addForm, addErrors, visitId, form, errors, conflict);
        }
    }

    private final PatientService patients;
    private final RecordsApi records;
    private final ZoneId zone;
    private final long maxUploadBytes;

    PatientPage(PatientService patients, RecordsApi records, @Value("${app.timezone}") ZoneId zone,
                @Value("${app.uploads.max-file-size}") org.springframework.util.unit.DataSize maxUpload) {
        this.patients = patients;
        this.records = records;
        this.zone = zone;
        this.maxUploadBytes = maxUpload.toBytes();
    }

    /** Initial Visits-tab state, optionally with one existing visit opened for editing (no-JS fallback of the edit link). */
    VisitState stateForEdit(UUID patientId, UUID editVisitId) {
        VisitState state = VisitState.initial(today());
        if (editVisitId == null) {
            return state;
        }
        return patients.visits(patientId).stream().filter(v -> v.id().equals(editVisitId)).findFirst()
                .map(v -> state.editing(v.id(), VisitForm.from(v.data(), v.version()), Map.of(), false))
                .orElse(state);
    }

    LocalDate today() {
        return LocalDate.now(zone);
    }

    PatientView load(UUID id) {
        return patients.get(id).orElseThrow(PatientNotFoundException::new);
    }

    /** Files tab state: which folder level is open and whether the grid or the list is shown. */
    record FilesState(UUID folderId, String view) {
        static final FilesState ROOT = new FilesState(null, "grid");
    }

    void fill(Model model, PatientView patient, String tab, VisitState visitState) {
        fill(model, patient, tab, visitState, FilesState.ROOT);
    }

    void fill(Model model, PatientView patient, String tab, VisitState visitState, FilesState filesState) {
        model.addAttribute("patient", patient);
        model.addAttribute("createdAt", local(patient.createdAt()));
        model.addAttribute("updatedAt", local(patient.updatedAt()));
        model.addAttribute("consentAt", patient.consentRecordedAt() == null ? null : local(patient.consentRecordedAt()));
        model.addAttribute("nav", "patients");
        String selected = "visits".equals(tab) ? "visits" : "files".equals(tab) ? "files" : "overview";
        model.addAttribute("tab", selected);
        if ("files".equals(selected)) {
            model.addAttribute("listing", records.browse(patient.id(), filesState.folderId()));
            model.addAttribute("fileView", filesState.view());
            model.addAttribute("zone", zone);
            model.addAttribute("maxUploadBytes", maxUploadBytes);
            model.addAttribute("maxUploadMb", maxUploadBytes / (1024 * 1024));
        }
        if ("visits".equals(selected)) {
            model.addAttribute("visits", patients.visits(patient.id()));
            model.addAttribute("addForm", visitState.addForm());
            model.addAttribute("addErrors", visitState.addErrors());
            model.addAttribute("editVisitId", visitState.editId());
            model.addAttribute("editForm", visitState.editForm());
            model.addAttribute("editErrors", visitState.editErrors());
            model.addAttribute("editConflict", visitState.editConflict());
        }
    }

    private LocalDateTime local(Instant instant) {
        return LocalDateTime.ofInstant(instant, zone);
    }
}
