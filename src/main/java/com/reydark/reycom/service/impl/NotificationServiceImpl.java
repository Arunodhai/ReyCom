package com.reydark.reycom.service.impl;

import com.reydark.reycom.dto.response.NotificationResponse;
import com.reydark.reycom.entity.Notification;
import com.reydark.reycom.entity.User;
import com.reydark.reycom.event.OrderEventMessage;
import com.reydark.reycom.event.PaymentEventMessage;
import com.reydark.reycom.exception.ResourceNotFoundException;
import com.reydark.reycom.exception.UnauthorizedException;
import com.reydark.reycom.mapper.NotificationMapper;
import com.reydark.reycom.repository.NotificationRepository;
import com.reydark.reycom.repository.UserRepository;
import com.reydark.reycom.security.UserPrincipal;
import com.reydark.reycom.service.NotificationService;
import com.reydark.reycom.service.ReyComMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ReyComMetricsService metricsService;

    @Override
    @Transactional
    public void createNotificationFromOrderEvent(OrderEventMessage event) {
        Optional<User> user = findEventUser(event.getUserId(), event.getEventType());
        if (user.isEmpty()) {
            return;
        }

        NotificationText notificationText = buildOrderNotification(event);
        notificationRepository.save(Notification.builder()
                .user(user.get())
                .title(notificationText.title())
                .message(notificationText.message())
                .eventType(event.getEventType())
                .referenceId(event.getOrderId())
                .read(false)
                .build());
        metricsService.recordNotificationCreated();
    }

    @Override
    @Transactional
    public void createNotificationFromPaymentEvent(PaymentEventMessage event) {
        Optional<User> user = findEventUser(event.getUserId(), event.getEventType());
        if (user.isEmpty()) {
            return;
        }

        NotificationText notificationText = buildPaymentNotification(event);
        notificationRepository.save(Notification.builder()
                .user(user.get())
                .title(notificationText.title())
                .message(notificationText.message())
                .eventType(event.getEventType())
                .referenceId(event.getPaymentId())
                .read(false)
                .build());
        metricsService.recordNotificationCreated();
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> getCurrentUserNotifications() {
        User user = getCurrentUser();
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(NotificationMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public NotificationResponse markNotificationAsRead(UUID notificationId) {
        User user = getCurrentUser();
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));

        notification.setRead(true);
        return NotificationMapper.toResponse(notification);
    }

    private Optional<User> findEventUser(String userId, String eventType) {
        try {
            return userRepository.findById(UUID.fromString(userId));
        } catch (RuntimeException ex) {
            log.warn("Skipping notification for event {} because userId is invalid: {}", eventType, userId);
            return Optional.empty();
        }
    }

    private NotificationText buildOrderNotification(OrderEventMessage event) {
        return switch (event.getEventType()) {
            case "ORDER_CREATED" -> new NotificationText(
                    "Order Created",
                    "Your order %s was created successfully.".formatted(event.getOrderNumber())
            );
            case "ORDER_CANCELLED" -> new NotificationText(
                    "Order Cancelled",
                    "Your order %s was cancelled successfully.".formatted(event.getOrderNumber())
            );
            case "ORDER_STATUS_UPDATED" -> new NotificationText(
                    "Order Status Updated",
                    "Your order %s status changed to %s.".formatted(event.getOrderNumber(), event.getOrderStatus())
            );
            default -> new NotificationText("Order Update", event.getMessage());
        };
    }

    private NotificationText buildPaymentNotification(PaymentEventMessage event) {
        return switch (event.getEventType()) {
            case "PAYMENT_INITIATED" -> new NotificationText(
                    "Payment Initiated",
                    "Payment for order %s was initiated.".formatted(event.getOrderNumber())
            );
            case "PAYMENT_SUCCESS" -> new NotificationText(
                    "Payment Successful",
                    "Payment for order %s was completed successfully.".formatted(event.getOrderNumber())
            );
            case "PAYMENT_FAILED" -> new NotificationText(
                    "Payment Failed",
                    "Payment for order %s failed.".formatted(event.getOrderNumber())
            );
            default -> new NotificationText("Payment Update", event.getMessage());
        };
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new UnauthorizedException("Authentication is required");
        }

        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new UnauthorizedException("Authentication is required"));
    }

    private record NotificationText(String title, String message) {
    }
}
