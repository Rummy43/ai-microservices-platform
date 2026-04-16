package com.ramesh.user_service.exception;

/**
 * Thrown when an operation conflicts with existing data (e.g., duplicate email).
 */
public class ResourceConflictException extends RuntimeException {
    public ResourceConflictException(String message) {
        super(message);
    }
}