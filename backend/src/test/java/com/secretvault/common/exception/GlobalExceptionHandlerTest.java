package com.secretvault.common.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        MDC.put("correlationId", "test-req-12345");
    }

    @Test
    void handleResourceNotFoundException_shouldReturn404AndStandardResponse() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Secret", "DB_PASS");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleApiException(ex);

        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("RESOURCE_NOT_FOUND", response.getBody().code());
        assertEquals("Secret not found with identifier: DB_PASS", response.getBody().message());
        assertEquals("test-req-12345", response.getBody().requestId());
    }

    @Test
    void handleAccessDeniedException_shouldReturn403() {
        AccessDeniedException ex = new AccessDeniedException("Access denied");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleAccessDeniedException(ex);

        assertNotNull(response);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("FORBIDDEN", response.getBody().code());
        assertEquals("test-req-12345", response.getBody().requestId());
    }

    @Test
    void handleAuthenticationException_shouldReturn401() {
        BadCredentialsException ex = new BadCredentialsException("Invalid token");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleAuthenticationException(ex);

        assertNotNull(response);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("UNAUTHORIZED", response.getBody().code());
        assertEquals("test-req-12345", response.getBody().requestId());
    }

    @Test
    void handleGeneralException_shouldReturn500AndSanitizedMessage() {
        RuntimeException ex = new RuntimeException("Database connection timeout at 10.0.0.1:5432");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleGeneralException(ex);

        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INTERNAL_SERVER_ERROR", response.getBody().code());
        assertFalse(response.getBody().message().contains("10.0.0.1"), "Should not leak internal infrastructure details");
        assertEquals("test-req-12345", response.getBody().requestId());
    }
}
