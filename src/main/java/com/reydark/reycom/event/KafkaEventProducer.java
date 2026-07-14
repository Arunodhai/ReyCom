package com.reydark.reycom.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${reycom.kafka.enabled:true}")
    private boolean kafkaEnabled;

    public void publishOrderEvent(OrderEventMessage event) {
        if (!kafkaEnabled) {
            log.debug("Kafka disabled. Skipping order event {}", event.getEventType());
            return;
        }

        try {
            kafkaTemplate.send(KafkaTopics.ORDER_EVENTS, event.getOrderId(), event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish order event {} for order {}", event.getEventType(), event.getOrderId(), ex);
                            return;
                        }
                        log.info("Published order event {} for order {}", event.getEventType(), event.getOrderId());
                    });
        } catch (RuntimeException ex) {
            log.error("Kafka publish failed before sending order event {} for order {}", event.getEventType(), event.getOrderId(), ex);
        }
    }

    public void publishPaymentEvent(PaymentEventMessage event) {
        if (!kafkaEnabled) {
            log.debug("Kafka disabled. Skipping payment event {}", event.getEventType());
            return;
        }

        try {
            kafkaTemplate.send(KafkaTopics.PAYMENT_EVENTS, event.getOrderId(), event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish payment event {} for payment {}", event.getEventType(), event.getPaymentId(), ex);
                            return;
                        }
                        log.info("Published payment event {} for payment {}", event.getEventType(), event.getPaymentId());
                    });
        } catch (RuntimeException ex) {
            log.error("Kafka publish failed before sending payment event {} for payment {}", event.getEventType(), event.getPaymentId(), ex);
        }
    }
}
