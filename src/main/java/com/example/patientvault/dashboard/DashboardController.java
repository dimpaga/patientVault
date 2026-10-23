package com.example.patientvault.dashboard;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.ui.Model;

@Controller
class DashboardController {

    @GetMapping("/")
    String dashboard(Model model) {
        model.addAttribute("nav", "dashboard");
        return "pages/dashboard";
    }
}
