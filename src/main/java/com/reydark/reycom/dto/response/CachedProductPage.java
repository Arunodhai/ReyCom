package com.reydark.reycom.dto.response;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;

public record CachedProductPage(
        List<ProductResponse> content,
        int page,
        int size,
        long totalElements,
        String sortBy,
        String sortDir
) {

    public static CachedProductPage from(Page<ProductResponse> productPage, String sortBy, String sortDir) {
        return new CachedProductPage(
                productPage.getContent(),
                productPage.getNumber(),
                productPage.getSize(),
                productPage.getTotalElements(),
                sortBy,
                sortDir
        );
    }

    public Page<ProductResponse> toPage() {
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return new PageImpl<>(
                content,
                PageRequest.of(page, size, Sort.by(direction, sortBy)),
                totalElements
        );
    }
}
