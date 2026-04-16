package com.ramesh.user_service.dto.response;

public record ApiResponse<T>(
        boolean success,
        String message,
        T data
) {}