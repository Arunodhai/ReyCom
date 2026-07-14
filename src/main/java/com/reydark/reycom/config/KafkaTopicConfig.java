package com.reydark.reycom.config;

import com.reydark.reycom.event.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;

@EnableKafka
@Configuration
@ConditionalOnProperty(name = "reycom.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaTopicConfig {

    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name(KafkaTopics.ORDER_EVENTS)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic paymentEventsTopic() {
        return TopicBuilder.name(KafkaTopics.PAYMENT_EVENTS)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
