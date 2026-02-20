package com.clearfolio.viewer.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lightweight endpoint used for liveness checks.
 */
@RestController
@RequestMapping("/healthz")
public class HealthController {

    /**
     * Returns a static health payload when the service is alive.
     *
     * @return health status payload
     */
    @GetMapping
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
