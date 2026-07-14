package com.reydark.reycom.mapper;

import com.reydark.reycom.dto.response.NotificationResponse;
import com.reydark.reycom.entity.Notification;

public final class NotificationMapper {

    private NotificationMapper() {
    }

    public static NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getEventType(),
                notification.getReferenceId(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}
