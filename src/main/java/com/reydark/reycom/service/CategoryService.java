package com.reydark.reycom.service;

import com.reydark.reycom.dto.request.CategoryRequest;
import com.reydark.reycom.dto.response.CategoryResponse;

import java.util.List;
import java.util.UUID;

public interface CategoryService {

    CategoryResponse create(CategoryRequest request);

    CategoryResponse update(UUID id, CategoryRequest request);

    void delete(UUID id);

    List<CategoryResponse> getAll();
}
