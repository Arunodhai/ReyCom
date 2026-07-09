package com.reydark.reycom.mapper;

import com.reydark.reycom.dto.response.InventoryResponse;
import com.reydark.reycom.entity.Inventory;

public final class InventoryMapper {

    private InventoryMapper() {
    }

    public static InventoryResponse toResponse(Inventory inventory) {
        return new InventoryResponse(
                inventory.getId(),
                inventory.getProduct().getId(),
                inventory.getProduct().getName(),
                inventory.getQuantityAvailable(),
                inventory.getReservedQuantity(),
                inventory.getAvailableToSell(),
                inventory.getLowStockThreshold(),
                inventory.isLowStock()
        );
    }
}
