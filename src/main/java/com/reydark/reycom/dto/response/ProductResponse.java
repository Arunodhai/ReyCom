package com.reydark.reycom.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String name,
        String description,
        BigDecimal price,
        String sku,
        String imageUrl,
        boolean active,
        CategoryResponse category,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
