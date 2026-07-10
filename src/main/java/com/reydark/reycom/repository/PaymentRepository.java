package com.reydark.reycom.repository;

import com.reydark.reycom.entity.Payment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    @EntityGraph(attributePaths = {"order", "user"})
    Optional<Payment> findByOrderId(UUID orderId);

    @EntityGraph(attributePaths = {"order", "user"})
    Optional<Payment> findByIdAndUserId(UUID paymentId, UUID userId);

    boolean existsByPaymentNumber(String paymentNumber);

    @EntityGraph(attributePaths = {"order", "user"})
    List<Payment> findByUserId(UUID userId);

    @Override
    @EntityGraph(attributePaths = {"order", "user"})
    List<Payment> findAll();
}
