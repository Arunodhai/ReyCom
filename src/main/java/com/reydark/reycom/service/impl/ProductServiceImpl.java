package com.reydark.reycom.service.impl;

import com.reydark.reycom.dto.request.ProductRequest;
import com.reydark.reycom.dto.response.ProductResponse;
import com.reydark.reycom.entity.Category;
import com.reydark.reycom.entity.Product;
import com.reydark.reycom.exception.BadRequestException;
import com.reydark.reycom.exception.ResourceNotFoundException;
import com.reydark.reycom.mapper.ProductMapper;
import com.reydark.reycom.repository.CategoryRepository;
import com.reydark.reycom.repository.ProductRepository;
import com.reydark.reycom.service.ProductService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "name",
            "price",
            "sku",
            "createdAt",
            "updatedAt"
    );

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    @Override
    @Transactional
    public ProductResponse create(ProductRequest request) {
        String normalizedSku = normalizeSku(request.sku());

        if (productRepository.existsBySkuIgnoreCase(normalizedSku)) {
            throw new BadRequestException("Product SKU already exists");
        }

        Category category = findCategory(request.categoryId());
        Product product = Product.builder()
                .name(request.name().trim())
                .description(trimToNull(request.description()))
                .price(request.price())
                .sku(normalizedSku)
                .imageUrl(trimToNull(request.imageUrl()))
                .active(true)
                .category(category)
                .build();

        return ProductMapper.toResponse(productRepository.save(product));
    }

    @Override
    @Transactional
    public ProductResponse update(UUID id, ProductRequest request) {
        Product product = findProduct(id);
        String normalizedSku = normalizeSku(request.sku());

        if (!product.getSku().equalsIgnoreCase(normalizedSku)
                && productRepository.existsBySkuIgnoreCase(normalizedSku)) {
            throw new BadRequestException("Product SKU already exists");
        }

        product.setName(request.name().trim());
        product.setDescription(trimToNull(request.description()));
        product.setPrice(request.price());
        product.setSku(normalizedSku);
        product.setImageUrl(trimToNull(request.imageUrl()));
        product.setCategory(findCategory(request.categoryId()));

        return ProductMapper.toResponse(product);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Product product = findProduct(id);
        product.setActive(false);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getById(UUID id) {
        Product product = productRepository.findOne(activeProducts().and(hasId(id)))
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        return ProductMapper.toResponse(product);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> getProducts(
            int page,
            int size,
            String sortBy,
            String sortDir,
            String search,
            UUID categoryId
    ) {
        Pageable pageable = PageRequest.of(
                normalizePage(page),
                normalizeSize(size),
                buildSort(sortBy, sortDir)
        );

        Specification<Product> specification = activeProducts()
                .and(nameContains(search))
                .and(categoryEquals(categoryId));

        return productRepository.findAll(specification, pageable)
                .map(ProductMapper::toResponse);
    }

    private Product findProduct(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    private Category findCategory(UUID id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
    }

    private Specification<Product> activeProducts() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.isTrue(root.get("active"));
    }

    private Specification<Product> hasId(UUID id) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("id"), id);
    }

    private Specification<Product> nameContains(String search) {
        return (root, query, criteriaBuilder) -> {
            if (search == null || search.isBlank()) {
                return criteriaBuilder.conjunction();
            }

            return criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("name")),
                    "%" + search.trim().toLowerCase() + "%"
            );
        };
    }

    private Specification<Product> categoryEquals(UUID categoryId) {
        return (root, query, criteriaBuilder) -> {
            if (categoryId == null) {
                return criteriaBuilder.conjunction();
            }

            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("category").get("id"), categoryId));
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Sort buildSort(String sortBy, String sortDir) {
        String field = sortBy == null || sortBy.isBlank() ? "createdAt" : sortBy.trim();

        if (!ALLOWED_SORT_FIELDS.contains(field)) {
            throw new BadRequestException("Unsupported sort field: " + field);
        }

        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }

    private int normalizePage(int page) {
        return Math.max(page, 0);
    }

    private int normalizeSize(int size) {
        if (size < 1) {
            return 10;
        }
        return Math.min(size, 100);
    }

    private String normalizeSku(String sku) {
        return sku.trim().toUpperCase();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
