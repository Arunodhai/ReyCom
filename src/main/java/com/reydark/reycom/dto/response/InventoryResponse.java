package com.reydark.reycom.dto.response;

import java.util.UUID;

public record InventoryResponse(
        UUID inventoryId,
        UUID productId,
        String productName,
        int quantityAvailable,
        int reservedQuantity,
        int availableToSell,
        int lowStockThreshold,
        boolean lowStock
) {
}
