package com.example.patientvault.dashboard;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.example.patientvault.patient.PatientApi;
import com.example.patientvault.shared.Permissions;

@Controller
class DashboardController {

    private static final int RECENT_PATIENTS = 5;

    private final PatientApi patients;

    DashboardController(PatientApi patients) {
        this.patients = patients;
    }

    @GetMapping("/")
    String dashboard(Model model) {
        model.addAttribute("nav", "dashboard");
        model.addAttribute("recentPatients", canReadPatients() ? patients.recent(RECENT_PATIENTS) : List.of());
        return "pages/dashboard";
    }

    private static boolean canReadPatients() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && auth.getAuthorities().stream().anyMatch(a -> Permissions.PATIENT_READ.equals(a.getAuthority()));
    }
}
