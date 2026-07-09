package com.reydark.reycom.repository;

import com.reydark.reycom.entity.Inventory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    @EntityGraph(attributePaths = "product")
    Optional<Inventory> findByProductId(UUID productId);

    boolean existsByProductId(UUID productId);
}
