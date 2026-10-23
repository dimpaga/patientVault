package com.example.patientvault.dashboard;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.example.patientvault.appointment.AppointmentApi;
import com.example.patientvault.patient.PatientApi;
import com.example.patientvault.shared.Permissions;

@Controller
class DashboardController {

    private static final int RECENT_PATIENTS = 5;

    private final PatientApi patients;
    private final AppointmentApi appointments;

    DashboardController(PatientApi patients, AppointmentApi appointments) {
        this.patients = patients;
        this.appointments = appointments;
    }

    @GetMapping("/")
    String dashboard(Model model) {
        boolean canRead = canReadPatients();
        model.addAttribute("nav", "dashboard");
        model.addAttribute("recentPatients", canRead ? patients.recent(RECENT_PATIENTS) : List.of());
        model.addAttribute("todayAppointments", canRead ? appointments.today() : List.of());
        model.addAttribute("failedReminders", canRead ? appointments.recentFailedReminders() : 0L);
        return "pages/dashboard";
    }

    private static boolean canReadPatients() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && auth.getAuthorities().stream().anyMatch(a -> Permissions.PATIENT_READ.equals(a.getAuthority()));
    }
}
