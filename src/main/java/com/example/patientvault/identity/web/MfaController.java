package com.example.patientvault.identity.web;

import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.example.patientvault.identity.internal.LoginCompletion;
import com.example.patientvault.identity.internal.MfaService;
import com.example.patientvault.identity.internal.PendingMfa;
import com.example.patientvault.identity.internal.SessionKeys;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Second login step. Reachable without authentication but only with a valid {@link PendingMfa} in the
 * session (created after a correct password). The session becomes authenticated only on success here.
 */
@Controller
class MfaController {

    private final MfaService mfa;
    private final LoginCompletion completion;
    private final Clock clock;

    MfaController(MfaService mfa, LoginCompletion completion, Clock clock) {
        this.mfa = mfa;
        this.completion = completion;
        this.clock = clock;
    }

    @GetMapping("/login/mfa")
    String page(HttpSession session, Model model) {
        PendingMfa pending = activePending(session);
        if (pending == null) {
            return "redirect:/login";
        }
        if (pending.enrol()) {
            model.addAttribute("secret", enrolment(session, pending).secretDisplay());
            return "pages/mfa-enroll";
        }
        return "pages/mfa-verify";
    }

    @GetMapping("/login/mfa/qr")
    @ResponseBody
    ResponseEntity<String> qr(HttpSession session) {
        PendingMfa pending = activePending(session);
        if (pending == null || !pending.enrol()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType("image/svg+xml"))
                .body(mfa.qrSvg(enrolment(session, pending).otpauthUri()));
    }

    @PostMapping("/login/mfa")
    void submit(@RequestParam(name = "code", defaultValue = "") String code, HttpServletRequest request,
                HttpServletResponse response, HttpSession session) throws IOException, ServletException {
        PendingMfa pending = activePending(session);
        if (pending == null) {
            response.sendRedirect(request.getContextPath() + "/login?expired");
            return;
        }

        if (pending.enrol()) {
            Object secret = session.getAttribute(SessionKeys.ENROL_SECRET);
            Optional<List<String>> codes = secret instanceof MfaService.Enrolment enrolment
                    ? mfa.confirmEnrolment(pending.userId(), enrolment.secret(), code) : Optional.empty();
            if (codes.isEmpty()) {
                response.sendRedirect(request.getContextPath() + "/login/mfa?error");
                return;
            }
            completion.establish(request, response, pending.userId());
            request.getSession().setAttribute(SessionKeys.RECOVERY_CODES, codes.get());
            response.sendRedirect(request.getContextPath() + "/mfa/recovery-codes");
            return;
        }

        switch (mfa.verifyLogin(pending.userId(), code)) {
            case TOTP_OK, RECOVERY_OK -> completion.complete(request, response, pending.userId());
            case LOCKED -> {
                session.removeAttribute(SessionKeys.PENDING_MFA);
                response.sendRedirect(request.getContextPath() + "/login?error");
            }
            case INVALID -> response.sendRedirect(request.getContextPath() + "/login/mfa?error");
        }
    }

    /** One-time display of freshly generated recovery codes. */
    @GetMapping("/mfa/recovery-codes")
    String recoveryCodes(HttpSession session, Model model) {
        Object codes = session.getAttribute(SessionKeys.RECOVERY_CODES);
        session.removeAttribute(SessionKeys.RECOVERY_CODES);
        if (!(codes instanceof List<?> list) || list.isEmpty()) {
            return "redirect:/";
        }
        model.addAttribute("codes", list);
        model.addAttribute("nav", "settings");
        return "pages/recovery-codes";
    }

    private PendingMfa activePending(HttpSession session) {
        if (session.getAttribute(SessionKeys.PENDING_MFA) instanceof PendingMfa pending) {
            if (!pending.isExpired(clock.instant())) {
                return pending;
            }
            session.removeAttribute(SessionKeys.PENDING_MFA);
            session.removeAttribute(SessionKeys.ENROL_SECRET);
        }
        return null;
    }

    private MfaService.Enrolment enrolment(HttpSession session, PendingMfa pending) {
        if (session.getAttribute(SessionKeys.ENROL_SECRET) instanceof MfaService.Enrolment existing) {
            return existing;
        }
        MfaService.Enrolment created = mfa.newEnrolment(pending.username());
        session.setAttribute(SessionKeys.ENROL_SECRET, created);
        return created;
    }
}
