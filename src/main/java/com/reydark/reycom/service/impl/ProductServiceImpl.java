package com.reydark.reycom.service.impl;

import com.reydark.reycom.dto.request.ProductRequest;
import com.reydark.reycom.dto.response.CachedProductPage;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
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
@Slf4j
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
    private final CacheManager cacheManager;

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "products", allEntries = true),
            @CacheEvict(cacheNames = "productDetails", allEntries = true)
    })
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
    @Caching(evict = {
            @CacheEvict(cacheNames = "products", allEntries = true),
            @CacheEvict(cacheNames = "productDetails", allEntries = true)
    })
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
    @Caching(evict = {
            @CacheEvict(cacheNames = "products", allEntries = true),
            @CacheEvict(cacheNames = "productDetails", allEntries = true)
    })
    public void delete(UUID id) {
        Product product = findProduct(id);
        product.setActive(false);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "productDetails", key = "#id.toString()")
    public ProductResponse getById(UUID id) {
        log.debug("Loading product {} from PostgreSQL", id);
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
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizeSize(size);
        String normalizedSortBy = normalizeSortBy(sortBy);
        String normalizedSortDir = normalizeSortDir(sortDir);
        String normalizedSearch = normalizeSearch(search);
        String productListCacheKey = productListCacheKey(
                normalizedPage,
                normalizedSize,
                normalizedSortBy,
                normalizedSortDir,
                normalizedSearch,
                categoryId
        );

        Cache productsCache = cacheManager.getCache("products");
        if (productsCache != null) {
            try {
                CachedProductPage cachedProductPage = productsCache.get(productListCacheKey, CachedProductPage.class);
                if (cachedProductPage != null) {
                    return cachedProductPage.toPage();
                }
            } catch (RuntimeException ex) {
                log.warn("Failed to read products cache key {}. Evicting stale entry.", productListCacheKey, ex);
                productsCache.evict(productListCacheKey);
            }
        }

        log.debug(
                "Loading products from PostgreSQL page={}, size={}, sortBy={}, sortDir={}, search={}, categoryId={}",
                normalizedPage,
                normalizedSize,
                normalizedSortBy,
                normalizedSortDir,
                normalizedSearch,
                categoryId
        );
        Pageable pageable = PageRequest.of(
                normalizedPage,
                normalizedSize,
                buildSort(normalizedSortBy, normalizedSortDir)
        );

        Specification<Product> specification = activeProducts()
                .and(nameContains(normalizedSearch))
                .and(categoryEquals(categoryId));

        Page<ProductResponse> productPage = productRepository.findAll(specification, pageable)
                .map(ProductMapper::toResponse);

        if (productsCache != null) {
            productsCache.put(productListCacheKey, CachedProductPage.from(productPage, normalizedSortBy, normalizedSortDir));
        }

        return productPage;
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
        String field = normalizeSortBy(sortBy);

        if (!ALLOWED_SORT_FIELDS.contains(field)) {
            throw new BadRequestException("Unsupported sort field: " + field);
        }

        Sort.Direction direction = "asc".equalsIgnoreCase(normalizeSortDir(sortDir))
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
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

    private String normalizeSortBy(String sortBy) {
        return sortBy == null || sortBy.isBlank() ? "createdAt" : sortBy.trim();
    }

    private String normalizeSortDir(String sortDir) {
        return sortDir == null || sortDir.isBlank() ? "desc" : sortDir.trim().toLowerCase();
    }

    private String normalizeSearch(String search) {
        return search == null ? "" : search.trim().toLowerCase();
    }

    private String productListCacheKey(
            int page,
            int size,
            String sortBy,
            String sortDir,
            String search,
            UUID categoryId
    ) {
        return "page=%d:size=%d:sortBy=%s:sortDir=%s:search=%s:categoryId=%s"
                .formatted(page, size, sortBy, sortDir, search, categoryId == null ? "all" : categoryId);
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
