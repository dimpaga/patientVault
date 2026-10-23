package com.example.patientvault.identity.web;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.patientvault.shared.Permissions;
import com.example.patientvault.shared.RequiresRecentAuth;
import com.example.patientvault.identity.internal.AppUserPrincipal;
import com.example.patientvault.identity.internal.MfaService;
import com.example.patientvault.identity.internal.PasswordPolicy;
import com.example.patientvault.identity.internal.PasswordService;
import com.example.patientvault.identity.internal.SessionKeys;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@Controller
@PreAuthorize("hasAuthority('" + Permissions.SETTINGS_MANAGE + "')")
class SettingsController {

    private final PasswordService passwords;
    private final MfaService mfa;

    SettingsController(PasswordService passwords, MfaService mfa) {
        this.passwords = passwords;
        this.mfa = mfa;
    }

    @GetMapping("/settings")
    String settings(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        model.addAttribute("nav", "settings");
        model.addAttribute("mfaStatus", mfa.status(principal.getId()));
        return "pages/settings";
    }

    @GetMapping("/settings/password")
    @RequiresRecentAuth
    String passwordForm(Model model) {
        model.addAttribute("nav", "settings");
        model.addAttribute("minLength", PasswordPolicy.MIN_LENGTH);
        return "pages/settings-password";
    }

    @PostMapping("/settings/password")
    @RequiresRecentAuth
    String changePassword(@AuthenticationPrincipal AppUserPrincipal principal,
                          @RequestParam(name = "currentPassword", defaultValue = "") String current,
                          @RequestParam(name = "newPassword", defaultValue = "") String next,
                          @RequestParam(name = "confirmPassword", defaultValue = "") String confirmation,
                          HttpServletRequest request, HttpSession session, Model model,
                          RedirectAttributes redirect) {
        List<String> problems = passwords.change(principal.getId(), current, next, confirmation);
        if (!problems.isEmpty()) {
            model.addAttribute("nav", "settings");
            model.addAttribute("minLength", PasswordPolicy.MIN_LENGTH);
            model.addAttribute("problems", problems);
            return "pages/settings-password";
        }
        session.setAttribute(SessionKeys.MUST_CHANGE_PASSWORD, false);
        request.changeSessionId();
        redirect.addFlashAttribute("flashSuccess", "settings.password.changed");
        return "redirect:/settings";
    }

    @PostMapping("/settings/recovery-codes")
    @RequiresRecentAuth
    String regenerateRecoveryCodes(@AuthenticationPrincipal AppUserPrincipal principal, HttpSession session) {
        session.setAttribute(SessionKeys.RECOVERY_CODES, mfa.regenerateRecoveryCodes(principal.getId()));
        return "redirect:/mfa/recovery-codes";
    }
}
