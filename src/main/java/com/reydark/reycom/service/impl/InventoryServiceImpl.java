package com.reydark.reycom.service.impl;

import com.reydark.reycom.dto.request.InventoryRequest;
import com.reydark.reycom.dto.request.InventoryUpdateRequest;
import com.reydark.reycom.dto.response.InventoryResponse;
import com.reydark.reycom.entity.Inventory;
import com.reydark.reycom.entity.Product;
import com.reydark.reycom.exception.BadRequestException;
import com.reydark.reycom.exception.ResourceNotFoundException;
import com.reydark.reycom.mapper.InventoryMapper;
import com.reydark.reycom.repository.InventoryRepository;
import com.reydark.reycom.repository.ProductRepository;
import com.reydark.reycom.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional
    public InventoryResponse createInventory(InventoryRequest request) {
        validateQuantities(
                request.quantityAvailable(),
                request.reservedQuantity(),
                request.lowStockThreshold()
        );

        Product product = findProduct(request.productId());

        if (inventoryRepository.existsByProductId(product.getId())) {
            throw new BadRequestException("Inventory already exists for this product");
        }

        Inventory inventory = Inventory.builder()
                .product(product)
                .quantityAvailable(request.quantityAvailable())
                .reservedQuantity(request.reservedQuantity())
                .lowStockThreshold(request.lowStockThreshold())
                .build();

        return InventoryMapper.toResponse(inventoryRepository.save(inventory));
    }

    @Override
    @Transactional
    public InventoryResponse updateInventory(UUID productId, InventoryUpdateRequest request) {
        validateQuantities(
                request.quantityAvailable(),
                request.reservedQuantity(),
                request.lowStockThreshold()
        );

        Inventory inventory = findInventoryByProductId(productId);
        inventory.setQuantityAvailable(request.quantityAvailable());
        inventory.setReservedQuantity(request.reservedQuantity());
        inventory.setLowStockThreshold(request.lowStockThreshold());

        return InventoryMapper.toResponse(inventory);
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryResponse getInventoryByProductId(UUID productId) {
        return InventoryMapper.toResponse(findInventoryByProductId(productId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryResponse> getAllInventory() {
        return inventoryRepository.findAll()
                .stream()
                .map(InventoryMapper::toResponse)
                .toList();
    }

    private Product findProduct(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    private Inventory findInventoryByProductId(UUID productId) {
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found"));
    }

    private void validateQuantities(int quantityAvailable, int reservedQuantity, int lowStockThreshold) {
        if (quantityAvailable < 0) {
            throw new BadRequestException("Quantity available must not be negative");
        }
        if (reservedQuantity < 0) {
            throw new BadRequestException("Reserved quantity must not be negative");
        }
        if (lowStockThreshold < 0) {
            throw new BadRequestException("Low stock threshold must not be negative");
        }
        if (reservedQuantity > quantityAvailable) {
            throw new BadRequestException("Reserved quantity cannot exceed quantity available");
        }
    }
}
