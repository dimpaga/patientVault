package com.example.patientvault.identity.web;

/** Guards post-action redirects against open-redirect abuse: only same-site relative paths are accepted. */
final class SafeRedirects {

    private SafeRedirects() {
    }

    static String relative(String target) {
        if (target == null || target.isBlank() || target.length() > 512) {
            return "/";
        }
        boolean safe = target.charAt(0) == '/'
                && !target.startsWith("//")
                && target.indexOf('\\') < 0
                && target.chars().noneMatch(c -> c < 0x20 || c == 0x7f);
        return safe ? target : "/";
    }
}
