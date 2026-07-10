package com.reydark.reycom.dto.response;

import com.reydark.reycom.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID orderId,
        String orderNumber,
        UUID userId,
        OrderStatus status,
        List<OrderItemResponse> items,
        BigDecimal totalAmount,
        LocalDateTime createdAt
) {
}
