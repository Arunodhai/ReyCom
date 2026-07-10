package com.reydark.reycom.dto.request;

import com.reydark.reycom.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;

public record InitiatePaymentRequest(
        @NotNull(message = "Payment method is required")
        PaymentMethod paymentMethod
) {
}
