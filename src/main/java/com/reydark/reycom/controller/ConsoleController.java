package com.reydark.reycom.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ConsoleController {

    @GetMapping({"/console", "/console/"})
    public String console() {
        return "redirect:/console/index.html";
    }
}
