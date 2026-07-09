package com.reydark.reycom.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductRequest(
        @NotBlank(message = "Product name is required")
        @Size(max = 180, message = "Product name must not exceed 180 characters")
        String name,

        @Size(max = 3000, message = "Product description must not exceed 3000 characters")
        String description,

        @NotNull(message = "Product price is required")
        @DecimalMin(value = "0.01", message = "Product price must be positive")
        BigDecimal price,

        @NotBlank(message = "Product SKU is required")
        @Size(max = 80, message = "Product SKU must not exceed 80 characters")
        String sku,

        @Size(max = 1000, message = "Image URL must not exceed 1000 characters")
        String imageUrl,

        @NotNull(message = "Category id is required")
        UUID categoryId
) {
}
