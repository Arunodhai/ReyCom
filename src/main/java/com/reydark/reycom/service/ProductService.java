package com.reydark.reycom.service;

import com.reydark.reycom.dto.request.ProductRequest;
import com.reydark.reycom.dto.response.ProductResponse;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface ProductService {

    ProductResponse create(ProductRequest request);

    ProductResponse update(UUID id, ProductRequest request);

    void delete(UUID id);

    ProductResponse getById(UUID id);

    Page<ProductResponse> getProducts(
            int page,
            int size,
            String sortBy,
            String sortDir,
            String search,
            UUID categoryId
    );
}
