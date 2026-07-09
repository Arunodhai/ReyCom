package com.reydark.reycom.mapper;

import com.reydark.reycom.dto.response.ProductResponse;
import com.reydark.reycom.entity.Product;

public final class ProductMapper {

    private ProductMapper() {
    }

    public static ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getSku(),
                product.getImageUrl(),
                product.isActive(),
                CategoryMapper.toResponse(product.getCategory()),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
