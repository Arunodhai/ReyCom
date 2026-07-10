package com.reydark.reycom.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PaymentFailureRequest(
        @NotBlank(message = "Failure reason is required")
        @Size(max = 1000, message = "Failure reason must not exceed 1000 characters")
        String failureReason
) {
}
