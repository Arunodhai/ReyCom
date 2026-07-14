package com.reydark.reycom.event;

import com.reydark.reycom.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "reycom.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaEventConsumer {

    private final NotificationService notificationService;

    @KafkaListener(topics = KafkaTopics.ORDER_EVENTS, groupId = "${spring.kafka.consumer.group-id}")
    public void consumeOrderEvent(OrderEventMessage event) {
        log.info("Consumed order event {} for order {}", event.getEventType(), event.getOrderId());
        notificationService.createNotificationFromOrderEvent(event);
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_EVENTS, groupId = "${spring.kafka.consumer.group-id}")
    public void consumePaymentEvent(PaymentEventMessage event) {
        log.info("Consumed payment event {} for payment {}", event.getEventType(), event.getPaymentId());
        notificationService.createNotificationFromPaymentEvent(event);
    }
}
