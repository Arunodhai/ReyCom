package com.reydark.reycom.dto.response;

import com.reydark.reycom.enums.PaymentMethod;
import com.reydark.reycom.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentResponse(
        UUID paymentId,
        String paymentNumber,
        UUID orderId,
        String orderNumber,
        UUID userId,
        BigDecimal amount,
        PaymentStatus status,
        PaymentMethod paymentMethod,
        String failureReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
