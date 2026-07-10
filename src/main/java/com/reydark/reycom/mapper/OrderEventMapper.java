package com.reydark.reycom.mapper;

import com.reydark.reycom.dto.response.OrderEventResponse;
import com.reydark.reycom.dynamodb.OrderEvent;

public final class OrderEventMapper {

    private OrderEventMapper() {
    }

    public static OrderEventResponse toResponse(OrderEvent event) {
        return new OrderEventResponse(
                event.getOrderId(),
                event.getEventTime(),
                event.getEventType(),
                event.getMessage(),
                event.getOrderStatus(),
                event.getPaymentStatus(),
                event.getPaymentId(),
                event.getUserId()
        );
    }
}
