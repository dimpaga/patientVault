package com.example.patientvault.identity.web;

import java.time.Clock;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.patientvault.identity.internal.AppUserPrincipal;
import com.example.patientvault.identity.internal.ReauthService;
import com.example.patientvault.identity.internal.SessionKeys;

import jakarta.servlet.http.HttpSession;

/** Asks for password + TOTP again before a sensitive action (spec 7.1: within the last 5 minutes). */
@Controller
class ReauthController {

    private final ReauthService reauth;
    private final Clock clock;

    ReauthController(ReauthService reauth, Clock clock) {
        this.reauth = reauth;
        this.clock = clock;
    }

    @GetMapping("/reauth")
    String form(@AuthenticationPrincipal AppUserPrincipal principal,
                @RequestParam(name = "next", required = false) String next, Model model) {
        model.addAttribute("next", SafeRedirects.relative(next));
        model.addAttribute("needsCode", principal.isTotpEnabled());
        model.addAttribute("nav", "settings");
        return "pages/reauth";
    }

    @PostMapping("/reauth")
    String submit(@AuthenticationPrincipal AppUserPrincipal principal,
                  @RequestParam(name = "password", defaultValue = "") String password,
                  @RequestParam(name = "code", defaultValue = "") String code,
                  @RequestParam(name = "next", required = false) String next, HttpSession session) {
        String target = SafeRedirects.relative(next);
        return switch (reauth.verify(principal.getId(), password, code)) {
            case OK -> {
                session.setAttribute(SessionKeys.REAUTH_AT, clock.instant());
                yield "redirect:" + target;
            }
            case LOCKED -> {
                session.invalidate();
                yield "redirect:/login?error";
            }
            case INVALID -> "redirect:/reauth?error&next=" + java.net.URLEncoder.encode(target,
                    java.nio.charset.StandardCharsets.UTF_8);
        };
    }
}
