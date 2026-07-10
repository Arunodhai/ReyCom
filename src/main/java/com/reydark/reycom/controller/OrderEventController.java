package com.reydark.reycom.controller;

import com.reydark.reycom.dto.response.ApiResponse;
import com.reydark.reycom.dto.response.OrderEventResponse;
import com.reydark.reycom.exception.ResourceNotFoundException;
import com.reydark.reycom.repository.OrderRepository;
import com.reydark.reycom.service.OrderEventService;
import com.reydark.reycom.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class OrderEventController {

    private final OrderEventService orderEventService;
    private final OrderService orderService;
    private final OrderRepository orderRepository;

    @GetMapping("/api/orders/{orderId}/events")
    public ResponseEntity<ApiResponse<List<OrderEventResponse>>> getCurrentUserOrderEvents(@PathVariable UUID orderId) {
        orderService.getCurrentUserOrderById(orderId);
        return ResponseEntity.ok(ApiResponse.success("Order events fetched successfully", orderEventService.getEventsForOrder(orderId)));
    }

    @GetMapping("/api/admin/orders/{orderId}/events")
    public ResponseEntity<ApiResponse<List<OrderEventResponse>>> getAdminOrderEvents(@PathVariable UUID orderId) {
        if (!orderRepository.existsById(orderId)) {
            throw new ResourceNotFoundException("Order not found");
        }
        return ResponseEntity.ok(ApiResponse.success("Order events fetched successfully", orderEventService.getEventsForOrder(orderId)));
    }
}
