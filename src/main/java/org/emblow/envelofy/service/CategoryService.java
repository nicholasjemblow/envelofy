/*
 * Copyright (C) 2025 Nicholas J Emblow
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.emblow.envelofy.service;

/**
 *
 * @author Nicholas J Emblow
 */
import org.emblow.envelofy.domain.Category;
import org.emblow.envelofy.domain.User;
import org.emblow.envelofy.repository.CategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class CategoryService {
    private static final Logger log = LoggerFactory.getLogger(CategoryService.class);
    
    private final CategoryRepository categoryRepository;
    private final SecurityService securityService;

    public CategoryService(
        CategoryRepository categoryRepository,
        SecurityService securityService
    ) {
        this.categoryRepository = categoryRepository;
        this.securityService = securityService;
    }

    @Transactional
    public Category create(String name, String description) {
        try {
            // Set the owner to the current user
            User currentUser = securityService.getCurrentUser();
            
            // Check if category already exists for this user
            Optional<Category> existing = categoryRepository.findByNameAndOwner(name, currentUser);
            if (existing.isPresent()) {
                throw new IllegalArgumentException("Category already exists: " + name);
            }
            
            Category category = new Category(name, description);
            category.setOwner(currentUser);
            
            return categoryRepository.save(category);
            
        } catch (Exception e) {
            log.error("Error creating category", e);
            throw new RuntimeException("Could not create category: " + e.getMessage());
        }
    }

    @Transactional
    public Category update(Long id, String name, String description) {
        try {
            Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found"));
                
            // Validate ownership
            securityService.validateOwnership(category.getOwner());
            
            category.setName(name);
            category.setDescription(description);
            
            return categoryRepository.save(category);
            
        } catch (Exception e) {
            log.error("Error updating category", e);
            throw new RuntimeException("Could not update category: " + e.getMessage());
        }
    }

    @Transactional
    public void delete(Long id) {
        try {
            Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found"));
                
            // Validate ownership
            securityService.validateOwnership(category.getOwner());
            
            categoryRepository.delete(category);
            
        } catch (Exception e) {
            log.error("Error deleting category", e);
            throw new RuntimeException("Could not delete category: " + e.getMessage());
        }
    }

    public List<Category> getAllForCurrentUser() {
        try {
            User currentUser = securityService.getCurrentUser();
            return categoryRepository.findByOwner(currentUser);
            
        } catch (Exception e) {
            log.error("Error retrieving categories", e);
            throw new RuntimeException("Could not retrieve categories: " + e.getMessage());
        }
    }

    public Category getById(Long id) {
        try {
            Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found"));
                
            // Validate ownership
            securityService.validateOwnership(category.getOwner());
            
            return category;
            
        } catch (Exception e) {
            log.error("Error retrieving category", e);
            throw new RuntimeException("Could not retrieve category: " + e.getMessage());
        }
    }

    // Helper method for services that need to do ownership validation
    public void validateOwnership(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
            .orElseThrow(() -> new RuntimeException("Category not found"));
        securityService.validateOwnership(category.getOwner());
    }
}
