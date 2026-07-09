package com.reydark.reycom.controller;

import com.reydark.reycom.dto.response.ApiResponse;
import com.reydark.reycom.dto.response.CategoryResponse;
import com.reydark.reycom.dto.response.ProductResponse;
import com.reydark.reycom.service.CategoryService;
import com.reydark.reycom.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final CategoryService categoryService;

    @GetMapping("/api/products")
    public ResponseEntity<ApiResponse<Page<ProductResponse>>> getProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID categoryId
    ) {
        Page<ProductResponse> products = productService.getProducts(page, size, sortBy, sortDir, search, categoryId);
        return ResponseEntity.ok(ApiResponse.success("Products fetched successfully", products));
    }

    @GetMapping("/api/products/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Product fetched successfully", productService.getById(id)));
    }

    @GetMapping("/api/categories")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getCategories() {
        return ResponseEntity.ok(ApiResponse.success("Categories fetched successfully", categoryService.getAll()));
    }
}
