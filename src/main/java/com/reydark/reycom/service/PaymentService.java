package com.reydark.reycom.service;

import com.reydark.reycom.dto.request.InitiatePaymentRequest;
import com.reydark.reycom.dto.request.PaymentFailureRequest;
import com.reydark.reycom.dto.response.PaymentResponse;

import java.util.List;
import java.util.UUID;

public interface PaymentService {

    PaymentResponse initiatePayment(UUID orderId, InitiatePaymentRequest request);

    PaymentResponse markPaymentSuccess(UUID paymentId);

    PaymentResponse markPaymentFailed(UUID paymentId, PaymentFailureRequest request);

    List<PaymentResponse> getCurrentUserPayments();

    PaymentResponse getCurrentUserPaymentById(UUID paymentId);

    List<PaymentResponse> getAllPaymentsForAdmin();
}
