package com.reydark.reycom.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record InventoryRequest(
        @NotNull(message = "Product id is required")
        UUID productId,

        @PositiveOrZero(message = "Quantity available must not be negative")
        int quantityAvailable,

        @PositiveOrZero(message = "Reserved quantity must not be negative")
        int reservedQuantity,

        @PositiveOrZero(message = "Low stock threshold must not be negative")
        int lowStockThreshold
) {
}
