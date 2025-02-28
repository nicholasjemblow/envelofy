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
package org.emblow.envelofy.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.validation.constraints.NotNull;

@Entity
public class Pattern {
    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotNull
    private String pattern;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    private PatternType type;
    
    @ManyToOne
    @NotNull
    private Category category;
    
    private Double confidence;
    
    @NotNull
    private Integer matchCount = 0;
    
    @NotNull
    private Double accuracy = 1.0;
    
    public enum PatternType {
        MERCHANT("Merchant name pattern"),
        TEMPORAL("Timing pattern"),
        AMOUNT("Amount pattern");
        
        private final String description;
        
        PatternType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    // Constructors
    public Pattern() {}
    
    public Pattern(String pattern, PatternType type, Category category) {
        this.pattern = pattern;
        this.type = type;
        this.category = category;
        this.confidence = 1.0;
    }
    
    // Business methods
    public void incrementMatch(boolean wasAccurate) {
        matchCount++;
        if (wasAccurate) {
            // Weighted moving average for accuracy
            accuracy = (accuracy * (matchCount - 1) + 1.0) / matchCount;
        } else {
            accuracy = (accuracy * (matchCount - 1)) / matchCount;
        }
        // Update confidence based on matches and accuracy
        confidence = (1.0 - (1.0 / matchCount)) * accuracy;
    }
    
    public boolean appliesTo(Transaction transaction) {
        return switch (type) {
            case MERCHANT -> matchesMerchant(transaction.getDescription());
            case TEMPORAL -> matchesTiming(transaction.getDate());
            case AMOUNT -> matchesAmount(transaction.getAmount());
        };
    }
    
    private boolean matchesMerchant(String description) {
        return description.toLowerCase().contains(pattern.toLowerCase());
    }
    
    private boolean matchesTiming(java.time.LocalDateTime date) {
        try {
            // Pattern format: DOW:HH or DOM:HH
            String[] parts = pattern.split(":");
            if (parts[0].startsWith("DOW")) {
                int dayOfWeek = Integer.parseInt(parts[0].substring(3));
                return date.getDayOfWeek().getValue() == dayOfWeek;
            } else if (parts[0].startsWith("DOM")) {
                int dayOfMonth = Integer.parseInt(parts[0].substring(3));
                return date.getDayOfMonth() == dayOfMonth;
            }
            return false;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    private boolean matchesAmount(java.math.BigDecimal amount) {
        try {
            // Pattern format: [operator][amount], e.g. >100 or =50.00
            char operator = pattern.charAt(0);
            double value = Double.parseDouble(pattern.substring(1));
            double txAmount = amount.doubleValue();
            
            return switch (operator) {
                case '>' -> txAmount > value;
                case '<' -> txAmount < value;
                case '=' -> Math.abs(txAmount - value) < 0.01;
                default -> false;
            };
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getPattern() { return pattern; }
    public void setPattern(String pattern) { this.pattern = pattern; }
    
    public PatternType getType() { return type; }
    public void setType(PatternType type) { this.type = type; }
    
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    
    public Integer getMatchCount() { return matchCount; }
    public void setMatchCount(Integer matchCount) { this.matchCount = matchCount; }
    
    public Double getAccuracy() { return accuracy; }
    public void setAccuracy(Double accuracy) { this.accuracy = accuracy; }
}