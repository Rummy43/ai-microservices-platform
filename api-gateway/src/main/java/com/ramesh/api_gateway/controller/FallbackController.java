package com.ramesh.api_gateway.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Circuit-breaker fallback endpoints. Reached via {@code forward:} when a
 * downstream service is unavailable or the breaker is open, so clients get a
 * fast, well-formed 503 instead of a hung connection or a raw stack trace.
 */
@RestController
@Slf4j
public class FallbackController {

    @RequestMapping("/fallback/users")
    public ResponseEntity<Map<String, String>> userServiceFallback() {
        log.warn("Circuit breaker fallback served | downstream: user-service");
        return serviceUnavailable("user-service");
    }

    @RequestMapping("/fallback/notifications")
    public ResponseEntity<Map<String, String>> notificationServiceFallback() {
        log.warn("Circuit breaker fallback served | downstream: notification-service");
        return serviceUnavailable("notification-service");
    }

    private ResponseEntity<Map<String, String>> serviceUnavailable(String service) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "10")
                .body(Map.of(
                        "error", "SERVICE_UNAVAILABLE",
                        "message", service + " is temporarily unavailable, please retry later",
                        "service", service
                ));
    }
}