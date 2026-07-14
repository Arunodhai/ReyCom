package com.reydark.reycom.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String title,
        String message,
        String eventType,
        String referenceId,
        boolean read,
        LocalDateTime createdAt
) {
}
