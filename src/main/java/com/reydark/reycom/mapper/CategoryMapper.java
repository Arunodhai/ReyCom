package com.reydark.reycom.mapper;

import com.reydark.reycom.dto.response.CategoryResponse;
import com.reydark.reycom.entity.Category;

public final class CategoryMapper {

    private CategoryMapper() {
    }

    public static CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.getCreatedAt(),
                category.getUpdatedAt()
        );
    }
}
