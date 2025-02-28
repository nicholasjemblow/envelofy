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
import org.emblow.envelofy.domain.Pattern;
import org.emblow.envelofy.domain.Pattern.PatternType;
import org.emblow.envelofy.domain.Category;
import org.emblow.envelofy.domain.Transaction;
import org.emblow.envelofy.domain.Envelope;
import org.emblow.envelofy.domain.User;
import org.emblow.envelofy.repository.PatternRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PatternService {
    private static final Logger log = LoggerFactory.getLogger(PatternService.class);
    private static final double MIN_CONFIDENCE = 0.3;
    
    private final PatternRepository patternRepository;
    private final CategoryService categoryService;
    private final SecurityService securityService;
    private final EnvelopeService envelopeService;

    public PatternService(
        PatternRepository patternRepository,
        CategoryService categoryService,
        SecurityService securityService,
        EnvelopeService envelopeService  // Add this parameter
    ) {
        this.patternRepository = patternRepository;
        this.categoryService = categoryService;
        this.securityService = securityService;
        this.envelopeService = envelopeService;  // Add this assignment
    }


    @Transactional
    public Pattern createPattern(String pattern, PatternType type, Long categoryId) {
        try {
            // Validate category ownership
            Category category = categoryService.getById(categoryId);
            
            // Check if pattern already exists
            Optional<Pattern> existing = patternRepository.findByPatternAndType(pattern, type);
            if (existing.isPresent()) {
                throw new IllegalArgumentException("Pattern already exists");
            }
            
            Pattern newPattern = new Pattern(pattern, type, category);
            return patternRepository.save(newPattern);
            
        } catch (IllegalArgumentException e) {
            log.error("Error creating pattern", e);
            throw new RuntimeException("Could not create pattern: " + e.getMessage());
        }
    }

    @Transactional
    public void delete(Long id) {
        try {
            Pattern pattern = patternRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pattern not found"));
                
            // Validate ownership through category
            categoryService.validateOwnership(pattern.getCategory().getId());
            
            patternRepository.delete(pattern);
            
        } catch (Exception e) {
            log.error("Error deleting pattern", e);
            throw new RuntimeException("Could not delete pattern: " + e.getMessage());
        }
    }

 public Map<Envelope, Double> suggestEnvelopes(Transaction transaction) {
    try {
        User currentUser = securityService.getCurrentUser();
        Map<Envelope, Double> suggestions = new HashMap<>();
        
        // Get confident patterns for current user
        List<Pattern> patterns = patternRepository.findConfidentPatterns(
            currentUser.getId(), 
            MIN_CONFIDENCE
        );
        
        // Track category scores first since patterns don't link directly to envelopes
        Map<Category, Double> categoryScores = new HashMap<>();
        
        // Match patterns against transaction
        for (Pattern pattern : patterns) {
            if (pattern.appliesTo(transaction)) {
                Category category = pattern.getCategory();
                categoryScores.merge(category, pattern.getConfidence(), Double::sum);
            }
        }
        
        // Normalize category scores
        double total = categoryScores.values().stream()
            .mapToDouble(Double::doubleValue)
            .sum();
            
        if (total > 0) {
            categoryScores.forEach((category, score) -> 
                categoryScores.put(category, score / total));
                
            // Find envelopes with matching categories and apply the scores
            List<Envelope> userEnvelopes = envelopeService.getAllEnvelopes();  // Need to inject EnvelopeService
            for (Envelope envelope : userEnvelopes) {
                // Match envelopes to categories based on names as a simple approach
                categoryScores.entrySet().stream()
                    .filter(entry -> envelope.getName().toLowerCase()
                        .contains(entry.getKey().getName().toLowerCase()))
                    .findFirst()
                    .ifPresent(entry -> suggestions.put(envelope, entry.getValue()));
            }
        }
        
        return suggestions;
        
    } catch (Exception e) {
        log.error("Error suggesting envelopes", e);
        throw new RuntimeException("Could not suggest envelopes: " + e.getMessage());
    }
}

    @Transactional
    public void learnFromTransaction(Transaction transaction, boolean wasCorrect) {
        try {
            User currentUser = securityService.getCurrentUser();
            List<Pattern> patterns = patternRepository.findByUserId(currentUser.getId());
            
            boolean patternFound = false;
            for (Pattern pattern : patterns) {
                if (pattern.appliesTo(transaction)) {
                    pattern.incrementMatch(wasCorrect);
                    patternFound = true;
                }
            }
            
            // If no patterns matched and the transaction was correct,
            // create new patterns based on the transaction
            if (!patternFound && wasCorrect) {
                createPatternsFromTransaction(transaction);
            }
            
        } catch (Exception e) {
            log.error("Error learning from transaction", e);
            throw new RuntimeException("Could not learn from transaction: " + e.getMessage());
        }
    }

    private void createPatternsFromTransaction(Transaction transaction) {
        try {
            Category category = transaction.getEnvelope().getCategory();
            if (category == null) {
                log.warn("No category found for envelope: {}", transaction.getEnvelope().getName());
                return;
            }
            
            // Create merchant pattern
            createPattern(
                transaction.getDescription().toLowerCase(),
                PatternType.MERCHANT,
                category.getId()
            );
            
            // Create temporal pattern
            LocalDateTime date = transaction.getDate();
            String temporalPattern;
            if (date.getDayOfMonth() <= 5 || date.getDayOfMonth() >= 25) {
                // Monthly pattern
                temporalPattern = "DOM" + date.getDayOfMonth() + ":" + date.getHour();
            } else {
                // Weekly pattern
                temporalPattern = "DOW" + date.getDayOfWeek().getValue() + ":" + date.getHour();
            }
            createPattern(temporalPattern, PatternType.TEMPORAL, category.getId());
            
            // Create amount pattern
            String amountPattern = "=" + transaction.getAmount().toString();
            createPattern(amountPattern, PatternType.AMOUNT, category.getId());
            
        } catch (Exception e) {
            log.error("Error creating patterns from transaction", e);
            // Don't throw - this is a best-effort pattern creation
        }
    }

    public List<Pattern> getPatternsByCategory(Long categoryId) {
        try {
            // Validate category ownership
            Category category = categoryService.getById(categoryId);
            return patternRepository.findByCategory(category);
            
        } catch (Exception e) {
            log.error("Error retrieving patterns by category", e);
            throw new RuntimeException("Could not retrieve patterns: " + e.getMessage());
        }
    }

    public List<Pattern> getPatternsByType(PatternType type) {
        try {
            User currentUser = securityService.getCurrentUser();
            // Filter patterns by user through the category owner
            return patternRepository.findByType(type).stream()
                .filter(p -> p.getCategory().getOwner().getId().equals(currentUser.getId()))
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("Error retrieving patterns by type", e);
            throw new RuntimeException("Could not retrieve patterns: " + e.getMessage());
        }
    }
}
