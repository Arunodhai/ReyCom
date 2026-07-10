package com.reydark.reycom.dto.response;

public record OrderEventResponse(
        String orderId,
        String eventTime,
        String eventType,
        String message,
        String orderStatus,
        String paymentStatus,
        String paymentId,
        String userId
) {
}
