package com.reydark.reycom.service.impl;

import com.reydark.reycom.dto.request.CategoryRequest;
import com.reydark.reycom.dto.response.CategoryResponse;
import com.reydark.reycom.entity.Category;
import com.reydark.reycom.exception.BadRequestException;
import com.reydark.reycom.exception.ResourceNotFoundException;
import com.reydark.reycom.mapper.CategoryMapper;
import com.reydark.reycom.repository.CategoryRepository;
import com.reydark.reycom.repository.ProductRepository;
import com.reydark.reycom.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String normalizedName = normalizeName(request.name());

        if (categoryRepository.existsByNameIgnoreCase(normalizedName)) {
            throw new BadRequestException("Category name already exists");
        }

        Category category = Category.builder()
                .name(normalizedName)
                .description(trimToNull(request.description()))
                .build();

        return CategoryMapper.toResponse(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public CategoryResponse update(UUID id, CategoryRequest request) {
        Category category = findCategory(id);
        String normalizedName = normalizeName(request.name());

        if (!category.getName().equalsIgnoreCase(normalizedName)
                && categoryRepository.existsByNameIgnoreCase(normalizedName)) {
            throw new BadRequestException("Category name already exists");
        }

        category.setName(normalizedName);
        category.setDescription(trimToNull(request.description()));

        return CategoryMapper.toResponse(category);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Category category = findCategory(id);

        if (productRepository.existsByCategoryId(id)) {
            throw new BadRequestException("Category cannot be deleted while products are assigned to it");
        }

        categoryRepository.delete(category);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAll() {
        return categoryRepository.findAll()
                .stream()
                .map(CategoryMapper::toResponse)
                .toList();
    }

    private Category findCategory(UUID id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
    }

    private String normalizeName(String name) {
        return name.trim();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
