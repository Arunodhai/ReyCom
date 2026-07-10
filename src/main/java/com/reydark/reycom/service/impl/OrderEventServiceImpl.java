package com.reydark.reycom.service.impl;

import com.reydark.reycom.dto.response.OrderEventResponse;
import com.reydark.reycom.dynamodb.OrderEvent;
import com.reydark.reycom.enums.OrderEventType;
import com.reydark.reycom.mapper.OrderEventMapper;
import com.reydark.reycom.repository.OrderEventDynamoRepository;
import com.reydark.reycom.service.OrderEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderEventServiceImpl implements OrderEventService {

    private final Optional<OrderEventDynamoRepository> orderEventRepository;

    @Override
    public void saveEvent(
            UUID orderId,
            OrderEventType eventType,
            String message,
            String orderStatus,
            String paymentStatus,
            UUID paymentId,
            UUID userId
    ) {
        if (orderEventRepository.isEmpty()) {
            log.debug("DynamoDB order event logging is disabled. Skipping event {} for order {}", eventType, orderId);
            return;
        }

        try {
            OrderEvent event = new OrderEvent();
            event.setOrderId(orderId.toString());
            event.setEventTime(Instant.now().toString());
            event.setEventType(eventType.name());
            event.setMessage(message);
            event.setOrderStatus(orderStatus);
            event.setPaymentStatus(paymentStatus);
            event.setPaymentId(paymentId == null ? null : paymentId.toString());
            event.setUserId(userId == null ? null : userId.toString());

            orderEventRepository.get().save(event);
        } catch (Exception ex) {
            log.error("Failed to save DynamoDB order event {} for order {}: {}", eventType, orderId, ex.getMessage(), ex);
        }
    }

    @Override
    public List<OrderEventResponse> getEventsForOrder(UUID orderId) {
        if (orderEventRepository.isEmpty()) {
            return List.of();
        }

        return orderEventRepository.get().findByOrderId(orderId.toString())
                .stream()
                .map(OrderEventMapper::toResponse)
                .toList();
    }
}
