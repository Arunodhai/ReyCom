package com.reydark.reycom.dto.request;

import jakarta.validation.constraints.Positive;

public record UpdateCartItemRequest(
        @Positive(message = "Quantity must be greater than 0")
        int quantity
) {
}
