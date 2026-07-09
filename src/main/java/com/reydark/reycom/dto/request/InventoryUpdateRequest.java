package com.reydark.reycom.dto.request;

import jakarta.validation.constraints.PositiveOrZero;

public record InventoryUpdateRequest(
        @PositiveOrZero(message = "Quantity available must not be negative")
        int quantityAvailable,

        @PositiveOrZero(message = "Reserved quantity must not be negative")
        int reservedQuantity,

        @PositiveOrZero(message = "Low stock threshold must not be negative")
        int lowStockThreshold
) {
}
