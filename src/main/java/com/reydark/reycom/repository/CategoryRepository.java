package com.reydark.reycom.repository;

import com.reydark.reycom.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    boolean existsByNameIgnoreCase(String name);
}
