package com.example.patientvault.appointment.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.patientvault.appointment.internal.ReminderProperties;
import com.example.patientvault.appointment.internal.ReminderSettingsService;
import com.example.patientvault.shared.Permissions;
import com.example.patientvault.shared.RequiresRecentAuth;

/**
 * Reminder settings: the doctor's notification address (editable, needs a recent login because reminders carry
 * patient names) and a read-only view of the deployment's reminder configuration.
 */
@Controller
class ReminderSettingsController {

    private final ReminderSettingsService settings;

    ReminderSettingsController(ReminderSettingsService settings) {
        this.settings = settings;
    }

    @GetMapping("/settings/reminders")
    @PreAuthorize("hasAuthority('" + Permissions.SETTINGS_MANAGE + "')")
    String page(Model model) {
        ReminderProperties preferences = settings.preferences();
        model.addAttribute("nav", "settings");
        model.addAttribute("email", settings.notificationEmail().orElse(""));
        model.addAttribute("prefs", preferences);
        return "pages/settings-reminders";
    }

    @PostMapping("/settings/reminders/email")
    @PreAuthorize("hasAuthority('" + Permissions.SETTINGS_MANAGE + "')")
    @RequiresRecentAuth
    String saveEmail(@RequestParam(name = "email", defaultValue = "") String email, Model model,
                     RedirectAttributes redirect) {
        if (!ReminderSettingsService.isValidEmail(email)) {
            ReminderProperties preferences = settings.preferences();
            model.addAttribute("nav", "settings");
            model.addAttribute("email", email);
            model.addAttribute("emailError", true);
            model.addAttribute("prefs", preferences);
            return "pages/settings-reminders";
        }
        settings.saveNotificationEmail(email);
        redirect.addFlashAttribute("flashSuccess", "settings.reminders.saved");
        return "redirect:/settings/reminders";
    }

    @PostMapping("/settings/reminders/test")
    @PreAuthorize("hasAuthority('" + Permissions.SETTINGS_MANAGE + "')")
    String sendTest(RedirectAttributes redirect) {
        ReminderSettingsService.Outcome outcome = settings.sendTest();
        redirect.addFlashAttribute("testOutcome", outcome.name());
        return "redirect:/settings/reminders";
    }
}
