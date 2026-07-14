package com.reydark.reycom.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderEventMessage {

    private String eventId;
    private String eventType;
    private String orderId;
    private String orderNumber;
    private String userId;
    private String orderStatus;
    private BigDecimal totalAmount;
    private String occurredAt;
    private String message;
}
