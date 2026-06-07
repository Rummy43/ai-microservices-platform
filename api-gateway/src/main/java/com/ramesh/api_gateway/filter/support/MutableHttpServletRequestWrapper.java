package com.ramesh.api_gateway.filter.support;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link HttpServletRequest} wrapper that lets us inject/override headers on an
 * in-flight request.
 *
 * <p>Spring Cloud Gateway MVC proxies the request that flows through the servlet
 * filter chain, so headers added here are forwarded to downstream services.
 *
 * <p>Header matching is case-insensitive, and any overridden header masks a
 * client-supplied header of the same name. This is what makes identity
 * propagation spoof-safe: a caller cannot smuggle their own {@code X-User-Roles}.
 */
public class MutableHttpServletRequestWrapper extends HttpServletRequestWrapper {

    private final Map<String, String> overriddenHeaders = new LinkedHashMap<>();

    public MutableHttpServletRequestWrapper(HttpServletRequest request) {
        super(request);
    }

    public void putHeader(String name, String value) {
        if (name != null && value != null) {
            overriddenHeaders.put(name, value);
        }
    }

    @Override
    public String getHeader(String name) {
        String override = findOverride(name);
        return override != null ? override : super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        String override = findOverride(name);
        if (override != null) {
            return Collections.enumeration(List.of(override));
        }
        return super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        Set<String> names = new LinkedHashSet<>();

        Enumeration<String> originalNames = super.getHeaderNames();
        while (originalNames.hasMoreElements()) {
            String name = originalNames.nextElement();
            // Drop any client-supplied copy of a header we override (anti-spoofing).
            if (findOverride(name) == null) {
                names.add(name);
            }
        }

        names.addAll(overriddenHeaders.keySet());
        return Collections.enumeration(names);
    }

    private String findOverride(String name) {
        if (name == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : overriddenHeaders.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }
}