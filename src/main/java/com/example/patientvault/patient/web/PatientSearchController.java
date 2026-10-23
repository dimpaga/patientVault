package com.example.patientvault.patient.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.patientvault.patient.internal.PatientSearchService;
import com.example.patientvault.shared.Permissions;

/** The patient list and the single search box (spec 8.2). Full page, or just the results for htmx. */
@Controller
class PatientSearchController {

    private final PatientSearchService searchService;

    PatientSearchController(PatientSearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/patients")
    @PreAuthorize("hasAuthority('" + Permissions.PATIENT_READ + "')")
    String list(@RequestParam(name = "q", defaultValue = "") String q,
                @RequestParam(name = "page", defaultValue = "1") String page,
                @RequestParam(name = "size", defaultValue = "" + PatientSearchService.DEFAULT_PAGE_SIZE) String size,
                @RequestHeader(name = "HX-Request", required = false) String htmx,
                @RequestHeader(name = "HX-Boosted", required = false) String boosted, Model model) {
        PatientSearchService.SearchPage result = searchService.search(q, number(page, 1), number(size, PatientSearchService.DEFAULT_PAGE_SIZE));
        model.addAttribute("q", q.strip());
        model.addAttribute("result", result);
        model.addAttribute("nav", "search");
        boolean resultsOnly = "true".equalsIgnoreCase(htmx) && !"true".equalsIgnoreCase(boosted);
        return resultsOnly ? "patients/results :: results" : "pages/patient-search";
    }

    /** Hand-edited URLs with a non-numeric page or size fall back to the default instead of failing. */
    private static int number(String value, int fallback) {
        try {
            return Integer.parseInt(value.strip());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
