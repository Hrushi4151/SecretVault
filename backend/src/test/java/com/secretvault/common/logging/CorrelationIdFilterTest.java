package com.secretvault.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CorrelationIdFilterTest {

    private CorrelationIdFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter();
        filterChain = mock(FilterChain.class);
    }

    @Test
    void doFilterInternal_withExistingHeader_shouldPropagateHeaderAndMDC() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "custom-trace-id-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        doAnswer(invocation -> {
            assertEquals("custom-trace-id-123", MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID_KEY));
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        assertEquals("custom-trace-id-123", response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER));
        assertEquals("custom-trace-id-123", response.getHeader(CorrelationIdFilter.REQUEST_ID_HEADER));
        assertNull(MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID_KEY), "MDC must be cleared after filter");
    }

    @Test
    void doFilterInternal_withoutHeader_shouldGenerateNewUuid() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        doAnswer(invocation -> {
            String mdcVal = MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID_KEY);
            assertNotNull(mdcVal);
            assertFalse(mdcVal.isBlank());
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        String responseHeader = response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
        assertNotNull(responseHeader);
        assertFalse(responseHeader.isBlank());
        assertNull(MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID_KEY), "MDC must be cleared after filter");
    }
}
