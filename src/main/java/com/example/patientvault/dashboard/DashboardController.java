package com.example.patientvault.dashboard;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.example.patientvault.appointment.AppointmentApi;
import com.example.patientvault.patient.PatientApi;
import com.example.patientvault.records.RecordsApi;
import com.example.patientvault.shared.Permissions;

@Controller
class DashboardController {

    private static final int RECENT_PATIENTS = 5;

    private final PatientApi patients;
    private final AppointmentApi appointments;
    private final RecordsApi records;

    DashboardController(PatientApi patients, AppointmentApi appointments, RecordsApi records) {
        this.patients = patients;
        this.appointments = appointments;
        this.records = records;
    }

    @GetMapping("/")
    String dashboard(Model model) {
        boolean canRead = has(Permissions.PATIENT_READ);
        model.addAttribute("nav", "dashboard");
        model.addAttribute("recentPatients", canRead ? patients.recent(RECENT_PATIENTS) : List.of());
        model.addAttribute("todayAppointments", canRead ? appointments.today() : List.of());
        model.addAttribute("patientCount", canRead ? patients.countActive() : 0L);
        model.addAttribute("fileCount", has(Permissions.FILE_READ) ? records.countAvailableFiles() : 0L);
        model.addAttribute("failedReminders", canRead ? appointments.recentFailedReminders() : 0L);
        return "pages/dashboard";
    }

    private static boolean has(String permission) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && auth.getAuthorities().stream().anyMatch(a -> permission.equals(a.getAuthority()));
    }
}
