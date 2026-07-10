package com.reydark.reycom.service.impl;

import com.reydark.reycom.dto.request.InitiatePaymentRequest;
import com.reydark.reycom.dto.request.PaymentFailureRequest;
import com.reydark.reycom.dto.response.PaymentResponse;
import com.reydark.reycom.entity.Order;
import com.reydark.reycom.entity.Payment;
import com.reydark.reycom.entity.User;
import com.reydark.reycom.enums.OrderEventType;
import com.reydark.reycom.enums.OrderStatus;
import com.reydark.reycom.enums.PaymentStatus;
import com.reydark.reycom.exception.BadRequestException;
import com.reydark.reycom.exception.ResourceNotFoundException;
import com.reydark.reycom.exception.UnauthorizedException;
import com.reydark.reycom.mapper.PaymentMapper;
import com.reydark.reycom.repository.OrderRepository;
import com.reydark.reycom.repository.PaymentRepository;
import com.reydark.reycom.repository.UserRepository;
import com.reydark.reycom.security.UserPrincipal;
import com.reydark.reycom.service.OrderEventService;
import com.reydark.reycom.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final DateTimeFormatter PAYMENT_NUMBER_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Set<OrderStatus> PAYABLE_ORDER_STATUSES = Set.of(
            OrderStatus.CREATED,
            OrderStatus.PAYMENT_PENDING
    );

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final OrderEventService orderEventService;

    @Override
    @Transactional
    public PaymentResponse initiatePayment(UUID orderId, InitiatePaymentRequest request) {
        if (request.paymentMethod() == null) {
            throw new BadRequestException("Payment method is required");
        }

        User user = getCurrentUser();
        Order order = orderRepository.findByIdAndUserId(orderId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        if (!PAYABLE_ORDER_STATUSES.contains(order.getStatus())) {
            throw new BadRequestException("Cannot initiate payment for order in its current status");
        }

        if (order.getStatus() == OrderStatus.CREATED) {
            order.setStatus(OrderStatus.PAYMENT_PENDING);
        }

        Payment payment = paymentRepository.findByOrderId(order.getId())
                .map(existingPayment -> reuseExistingPayment(existingPayment, request))
                .orElseGet(() -> createPayment(order, user, request));
        orderEventService.saveEvent(
                order.getId(),
                OrderEventType.PAYMENT_INITIATED,
                "Payment initiated",
                order.getStatus().name(),
                payment.getStatus().name(),
                payment.getId(),
                user.getId()
        );

        return PaymentMapper.toResponse(payment);
    }

    @Override
    @Transactional
    public PaymentResponse markPaymentSuccess(UUID paymentId) {
        User user = getCurrentUser();
        Payment payment = findCurrentUserPayment(paymentId, user);

        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            throw new BadRequestException("Payment already completed");
        }
        if (payment.getStatus() != PaymentStatus.INITIATED) {
            throw new BadRequestException("Only initiated payments can be marked successful");
        }

        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setFailureReason(null);
        payment.getOrder().setStatus(OrderStatus.PAID);
        orderEventService.saveEvent(
                payment.getOrder().getId(),
                OrderEventType.PAYMENT_SUCCESS,
                "Payment completed successfully",
                payment.getOrder().getStatus().name(),
                payment.getStatus().name(),
                payment.getId(),
                user.getId()
        );

        return PaymentMapper.toResponse(payment);
    }

    @Override
    @Transactional
    public PaymentResponse markPaymentFailed(UUID paymentId, PaymentFailureRequest request) {
        User user = getCurrentUser();
        Payment payment = findCurrentUserPayment(paymentId, user);

        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            throw new BadRequestException("Successful payment cannot be marked failed");
        }
        if (payment.getStatus() != PaymentStatus.INITIATED) {
            throw new BadRequestException("Only initiated payments can be marked failed");
        }

        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason(request.failureReason().trim());
        payment.getOrder().setStatus(OrderStatus.PAYMENT_PENDING);
        orderEventService.saveEvent(
                payment.getOrder().getId(),
                OrderEventType.PAYMENT_FAILED,
                "Payment failed",
                payment.getOrder().getStatus().name(),
                payment.getStatus().name(),
                payment.getId(),
                user.getId()
        );

        return PaymentMapper.toResponse(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getCurrentUserPayments() {
        User user = getCurrentUser();
        return paymentRepository.findByUserId(user.getId())
                .stream()
                .map(PaymentMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getCurrentUserPaymentById(UUID paymentId) {
        User user = getCurrentUser();
        return PaymentMapper.toResponse(findCurrentUserPayment(paymentId, user));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getAllPaymentsForAdmin() {
        return paymentRepository.findAll()
                .stream()
                .map(PaymentMapper::toResponse)
                .toList();
    }

    private Payment createPayment(Order order, User user, InitiatePaymentRequest request) {
        Payment payment = Payment.builder()
                .paymentNumber(generatePaymentNumber())
                .order(order)
                .user(user)
                .amount(order.getTotalAmount())
                .status(PaymentStatus.INITIATED)
                .paymentMethod(request.paymentMethod())
                .build();

        return paymentRepository.save(payment);
    }

    private Payment reuseExistingPayment(Payment payment, InitiatePaymentRequest request) {
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            throw new BadRequestException("Payment already completed for this order");
        }

        payment.setStatus(PaymentStatus.INITIATED);
        payment.setPaymentMethod(request.paymentMethod());
        payment.setAmount(payment.getOrder().getTotalAmount());
        payment.setFailureReason(null);
        return payment;
    }

    private Payment findCurrentUserPayment(UUID paymentId, User user) {
        return paymentRepository.findByIdAndUserId(paymentId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new UnauthorizedException("Authentication is required");
        }

        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new UnauthorizedException("Authentication is required"));
    }

    private String generatePaymentNumber() {
        String paymentNumber;

        do {
            String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            paymentNumber = "PAY-" + LocalDateTime.now().format(PAYMENT_NUMBER_TIMESTAMP) + "-" + suffix;
        } while (paymentRepository.existsByPaymentNumber(paymentNumber));

        return paymentNumber;
    }
}
