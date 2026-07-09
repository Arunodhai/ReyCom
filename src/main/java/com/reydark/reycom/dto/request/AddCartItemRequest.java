package com.reydark.reycom.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record AddCartItemRequest(
        @NotNull(message = "Product id is required")
        UUID productId,

        @Positive(message = "Quantity must be greater than 0")
        int quantity
) {
}
