package com.reydark.reycom.service;

import com.reydark.reycom.dto.response.NotificationResponse;
import com.reydark.reycom.event.OrderEventMessage;
import com.reydark.reycom.event.PaymentEventMessage;

import java.util.List;
import java.util.UUID;

public interface NotificationService {

    void createNotificationFromOrderEvent(OrderEventMessage event);

    void createNotificationFromPaymentEvent(PaymentEventMessage event);

    List<NotificationResponse> getCurrentUserNotifications();

    NotificationResponse markNotificationAsRead(UUID notificationId);
}
