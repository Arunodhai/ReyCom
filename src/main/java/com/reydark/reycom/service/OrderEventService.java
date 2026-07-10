package com.reydark.reycom.service;

import com.reydark.reycom.dto.response.OrderEventResponse;
import com.reydark.reycom.enums.OrderEventType;

import java.util.List;
import java.util.UUID;

public interface OrderEventService {

    void saveEvent(
            UUID orderId,
            OrderEventType eventType,
            String message,
            String orderStatus,
            String paymentStatus,
            UUID paymentId,
            UUID userId
    );

    List<OrderEventResponse> getEventsForOrder(UUID orderId);
}
