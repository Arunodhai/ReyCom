package com.reydark.reycom.service;

import com.reydark.reycom.dto.request.UpdateOrderStatusRequest;
import com.reydark.reycom.dto.response.OrderResponse;

import java.util.List;
import java.util.UUID;

public interface OrderService {

    OrderResponse createOrderFromCart();

    List<OrderResponse> getCurrentUserOrders();

    OrderResponse getCurrentUserOrderById(UUID orderId);

    OrderResponse cancelOrder(UUID orderId);

    List<OrderResponse> getAllOrdersForAdmin();

    OrderResponse updateOrderStatusForAdmin(UUID orderId, UpdateOrderStatusRequest request);
}
