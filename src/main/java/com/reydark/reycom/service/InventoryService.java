package com.reydark.reycom.service;

import com.reydark.reycom.dto.request.InventoryRequest;
import com.reydark.reycom.dto.request.InventoryUpdateRequest;
import com.reydark.reycom.dto.response.InventoryResponse;

import java.util.List;
import java.util.UUID;

public interface InventoryService {

    InventoryResponse createInventory(InventoryRequest request);

    InventoryResponse updateInventory(UUID productId, InventoryUpdateRequest request);

    InventoryResponse getInventoryByProductId(UUID productId);

    List<InventoryResponse> getAllInventory();
}
