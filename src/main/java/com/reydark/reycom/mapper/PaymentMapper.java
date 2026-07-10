package com.reydark.reycom.mapper;

import com.reydark.reycom.dto.response.PaymentResponse;
import com.reydark.reycom.entity.Payment;

public final class PaymentMapper {

    private PaymentMapper() {
    }

    public static PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getPaymentNumber(),
                payment.getOrder().getId(),
                payment.getOrder().getOrderNumber(),
                payment.getUser().getId(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getPaymentMethod(),
                payment.getFailureReason(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}
