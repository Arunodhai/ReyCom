package com.reydark.reycom.controller;

import com.reydark.reycom.dto.request.InitiatePaymentRequest;
import com.reydark.reycom.dto.request.PaymentFailureRequest;
import com.reydark.reycom.dto.response.ApiResponse;
import com.reydark.reycom.dto.response.PaymentResponse;
import com.reydark.reycom.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/api/orders/{orderId}/payments")
    public ResponseEntity<ApiResponse<PaymentResponse>> initiatePayment(
            @PathVariable UUID orderId,
            @Valid @RequestBody InitiatePaymentRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Payment initiated successfully", paymentService.initiatePayment(orderId, request)));
    }

    @PostMapping("/api/payments/{paymentId}/success")
    public ResponseEntity<ApiResponse<PaymentResponse>> markPaymentSuccess(@PathVariable UUID paymentId) {
        return ResponseEntity.ok(ApiResponse.success("Payment marked successful", paymentService.markPaymentSuccess(paymentId)));
    }

    @PostMapping("/api/payments/{paymentId}/fail")
    public ResponseEntity<ApiResponse<PaymentResponse>> markPaymentFailed(
            @PathVariable UUID paymentId,
            @Valid @RequestBody PaymentFailureRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Payment marked failed", paymentService.markPaymentFailed(paymentId, request)));
    }

    @GetMapping("/api/payments")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getCurrentUserPayments() {
        return ResponseEntity.ok(ApiResponse.success("Payments fetched successfully", paymentService.getCurrentUserPayments()));
    }

    @GetMapping("/api/payments/{paymentId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getCurrentUserPaymentById(@PathVariable UUID paymentId) {
        return ResponseEntity.ok(ApiResponse.success("Payment fetched successfully", paymentService.getCurrentUserPaymentById(paymentId)));
    }
}
