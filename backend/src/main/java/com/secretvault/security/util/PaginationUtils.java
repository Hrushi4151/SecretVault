package com.secretvault.security.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Utility enforcing strict server-side sort whitelist and page size boundaries (DoS protection).
 */
public final class PaginationUtils {

    public static final int MAX_PAGE_SIZE = 100;
    public static final int DEFAULT_PAGE_SIZE = 20;

    private PaginationUtils() {
    }

    public static Pageable sanitizePageable(Pageable pageable, Set<String> allowedSortFields, String defaultSortField, Sort.Direction defaultDirection) {
        int page = pageable != null ? Math.max(0, pageable.getPageNumber()) : 0;
        int size = pageable != null ? Math.max(1, Math.min(MAX_PAGE_SIZE, pageable.getPageSize())) : DEFAULT_PAGE_SIZE;

        if (pageable == null || pageable.getSort().isUnsorted()) {
            return PageRequest.of(page, size, Sort.by(defaultDirection != null ? defaultDirection : Sort.Direction.DESC, defaultSortField));
        }

        List<Sort.Order> safeOrders = new ArrayList<>();
        for (Sort.Order order : pageable.getSort()) {
            if (allowedSortFields != null && allowedSortFields.contains(order.getProperty())) {
                safeOrders.add(new Sort.Order(order.getDirection(), order.getProperty()));
            }
        }

        if (safeOrders.isEmpty()) {
            return PageRequest.of(page, size, Sort.by(defaultDirection != null ? defaultDirection : Sort.Direction.DESC, defaultSortField));
        }

        return PageRequest.of(page, size, Sort.by(safeOrders));
    }
}
