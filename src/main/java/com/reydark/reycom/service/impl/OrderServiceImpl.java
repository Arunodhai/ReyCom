package com.reydark.reycom.service.impl;

import com.reydark.reycom.dto.request.UpdateOrderStatusRequest;
import com.reydark.reycom.dto.response.OrderResponse;
import com.reydark.reycom.entity.Cart;
import com.reydark.reycom.entity.CartItem;
import com.reydark.reycom.entity.Inventory;
import com.reydark.reycom.entity.Order;
import com.reydark.reycom.entity.OrderItem;
import com.reydark.reycom.entity.User;
import com.reydark.reycom.enums.OrderEventType;
import com.reydark.reycom.enums.OrderStatus;
import com.reydark.reycom.event.KafkaEventProducer;
import com.reydark.reycom.event.OrderEventMessage;
import com.reydark.reycom.exception.BadRequestException;
import com.reydark.reycom.exception.ResourceNotFoundException;
import com.reydark.reycom.exception.UnauthorizedException;
import com.reydark.reycom.mapper.OrderMapper;
import com.reydark.reycom.repository.CartRepository;
import com.reydark.reycom.repository.InventoryRepository;
import com.reydark.reycom.repository.OrderRepository;
import com.reydark.reycom.repository.UserRepository;
import com.reydark.reycom.security.UserPrincipal;
import com.reydark.reycom.service.OrderEventService;
import com.reydark.reycom.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final DateTimeFormatter ORDER_NUMBER_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Set<OrderStatus> CUSTOMER_CANCELLABLE_STATUSES = Set.of(
            OrderStatus.CREATED,
            OrderStatus.PAYMENT_PENDING
    );

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final InventoryRepository inventoryRepository;
    private final UserRepository userRepository;
    private final OrderEventService orderEventService;
    private final KafkaEventProducer kafkaEventProducer;

    @Override
    @Transactional
    public OrderResponse createOrderFromCart() {
        User user = getCurrentUser();
        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseThrow(() -> new BadRequestException("Cart is empty"));

        if (cart.getItems().isEmpty()) {
            throw new BadRequestException("Cart is empty");
        }

        Order order = Order.builder()
                .orderNumber(generateOrderNumber())
                .user(user)
                .status(OrderStatus.PAYMENT_PENDING)
                .totalAmount(BigDecimal.ZERO)
                .build();

        BigDecimal totalAmount = BigDecimal.ZERO;

        for (CartItem cartItem : cart.getItems()) {
            Inventory inventory = findInventory(cartItem);

            if (inventory.getAvailableToSell() < cartItem.getQuantity()) {
                throw new BadRequestException("Insufficient inventory for product: " + cartItem.getProduct().getName());
            }

            inventory.setQuantityAvailable(inventory.getQuantityAvailable() - cartItem.getQuantity());

            OrderItem orderItem = OrderItem.builder()
                    .product(cartItem.getProduct())
                    .productNameSnapshot(cartItem.getProduct().getName())
                    .quantity(cartItem.getQuantity())
                    .unitPrice(cartItem.getUnitPrice())
                    .lineTotal(cartItem.getLineTotal())
                    .build();
            orderItem.recalculateLineTotal();
            order.addItem(orderItem);

            totalAmount = totalAmount.add(orderItem.getLineTotal());
        }

        order.setTotalAmount(totalAmount);
        Order savedOrder = orderRepository.save(order);
        cart.getItems().clear();
        orderEventService.saveEvent(
                savedOrder.getId(),
                OrderEventType.ORDER_CREATED,
                "Order created successfully",
                savedOrder.getStatus().name(),
                null,
                null,
                user.getId()
        );
        publishOrderEventAfterCommit(savedOrder, OrderEventType.ORDER_CREATED.name(), "Order created successfully");

        return OrderMapper.toResponse(savedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getCurrentUserOrders() {
        User user = getCurrentUser();
        return orderRepository.findByUserId(user.getId())
                .stream()
                .map(OrderMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getCurrentUserOrderById(UUID orderId) {
        User user = getCurrentUser();
        Order order = orderRepository.findByIdAndUserId(orderId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        return OrderMapper.toResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(UUID orderId) {
        User user = getCurrentUser();
        Order order = orderRepository.findByIdAndUserId(orderId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        if (!CUSTOMER_CANCELLABLE_STATUSES.contains(order.getStatus())) {
            throw new BadRequestException("Order cannot be cancelled in its current status");
        }

        restoreInventory(order);
        order.setStatus(OrderStatus.CANCELLED);
        orderEventService.saveEvent(
                order.getId(),
                OrderEventType.ORDER_CANCELLED,
                "Order cancelled successfully",
                order.getStatus().name(),
                null,
                null,
                user.getId()
        );
        publishOrderEventAfterCommit(order, OrderEventType.ORDER_CANCELLED.name(), "Order cancelled successfully");

        return OrderMapper.toResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrdersForAdmin() {
        return orderRepository.findAll()
                .stream()
                .map(OrderMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public OrderResponse updateOrderStatusForAdmin(UUID orderId, UpdateOrderStatusRequest request) {
        if (request.status() == null) {
            throw new BadRequestException("Order status is required");
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        order.setStatus(request.status());
        orderEventService.saveEvent(
                order.getId(),
                OrderEventType.ORDER_STATUS_UPDATED,
                "Order status updated to " + request.status().name(),
                order.getStatus().name(),
                null,
                null,
                order.getUser().getId()
        );
        publishOrderEventAfterCommit(order, OrderEventType.ORDER_STATUS_UPDATED.name(), "Order status updated to " + request.status().name());

        return OrderMapper.toResponse(order);
    }

    private void publishOrderEventAfterCommit(Order order, String eventType, String message) {
        OrderEventMessage event = OrderEventMessage.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .orderId(order.getId().toString())
                .orderNumber(order.getOrderNumber())
                .userId(order.getUser().getId().toString())
                .orderStatus(order.getStatus().name())
                .totalAmount(order.getTotalAmount())
                .occurredAt(Instant.now().toString())
                .message(message)
                .build();

        runAfterCommit(() -> kafkaEventProducer.publishOrderEvent(event));
    }

    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new UnauthorizedException("Authentication is required");
        }

        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new UnauthorizedException("Authentication is required"));
    }

    private Inventory findInventory(CartItem cartItem) {
        return inventoryRepository.findByProductId(cartItem.getProduct().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for product: " + cartItem.getProduct().getName()));
    }

    private Inventory findInventory(OrderItem orderItem) {
        return inventoryRepository.findByProductId(orderItem.getProduct().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for product: " + orderItem.getProductNameSnapshot()));
    }

    private void restoreInventory(Order order) {
        for (OrderItem item : order.getItems()) {
            Inventory inventory = findInventory(item);
            inventory.setQuantityAvailable(inventory.getQuantityAvailable() + item.getQuantity());
        }
    }

    private String generateOrderNumber() {
        String orderNumber;

        do {
            String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            orderNumber = "ORD-" + LocalDateTime.now().format(ORDER_NUMBER_TIMESTAMP) + "-" + suffix;
        } while (orderRepository.existsByOrderNumber(orderNumber));

        return orderNumber;
    }
}
