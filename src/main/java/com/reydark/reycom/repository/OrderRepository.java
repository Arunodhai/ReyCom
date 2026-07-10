package com.reydark.reycom.repository;

import com.reydark.reycom.entity.Order;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    @EntityGraph(attributePaths = {"user", "items", "items.product"})
    List<Order> findByUserId(UUID userId);

    @EntityGraph(attributePaths = {"user", "items", "items.product"})
    Optional<Order> findByIdAndUserId(UUID id, UUID userId);

    @EntityGraph(attributePaths = {"user", "items", "items.product"})
    Optional<Order> findByOrderNumber(String orderNumber);

    boolean existsByOrderNumber(String orderNumber);

    @Override
    @EntityGraph(attributePaths = {"user", "items", "items.product"})
    List<Order> findAll();
}
