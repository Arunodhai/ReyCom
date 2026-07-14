package com.reydark.reycom.controller;

import com.reydark.reycom.dto.response.ApiResponse;
import com.reydark.reycom.dto.response.NotificationResponse;
import com.reydark.reycom.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getCurrentUserNotifications() {
        return ResponseEntity.ok(ApiResponse.success("Notifications fetched successfully", notificationService.getCurrentUserNotifications()));
    }

    @PutMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markNotificationAsRead(@PathVariable UUID notificationId) {
        return ResponseEntity.ok(ApiResponse.success("Notification marked as read", notificationService.markNotificationAsRead(notificationId)));
    }
}
