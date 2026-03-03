package com.proconsi.electrobazar.controller.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FaviconController {

    /**
     * Handles the common browser request for favicon.ico to avoid
     * 404/NoResourceFoundException warnings.
     * Forwards to the actual SVG icon.
     */
    @GetMapping("favicon.ico")
    public String favicon() {
        return "forward:/icons/favicon.svg";
    }
}
