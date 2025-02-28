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
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.emblow.envelofy.domain.TransactionType;  

@Entity
public class RecurringTransaction {
    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotNull
    private String description;
    
    @NotNull
    private BigDecimal amount;
    
    @NotNull
    @ManyToOne
    private Envelope envelope;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    private RecurrencePattern pattern;
    
    private LocalDateTime nextDueDate;
    private LocalDateTime lastProcessed;
    
    @NotNull
    @ManyToOne
    private Account account;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    private TransactionType type;  // New field for classification (e.g., INCOME or EXPENSE)
    
    public enum RecurrencePattern {
        DAILY,
        WEEKLY,
        BIWEEKLY,
        MONTHLY,
        YEARLY
    }
    
    // Default constructor
    public RecurringTransaction() {}
    
    // Full constructor updated to include transaction type
    public RecurringTransaction(
        String description,
        BigDecimal amount,
        Envelope envelope,
        Account account,
        RecurrencePattern pattern,
        TransactionType type
    ) {
        this.description = description;
        this.amount = amount;
        this.envelope = envelope;
        this.account = account;
        this.pattern = pattern;
        this.type = type;
        this.nextDueDate = calculateNextDueDate();
    }
    
    public LocalDateTime calculateNextDueDate() {
        LocalDateTime base = (lastProcessed != null) ? lastProcessed : LocalDateTime.now();
        return switch (pattern) {
            case DAILY -> base.plusDays(1);
            case WEEKLY -> base.plusWeeks(1);
            case BIWEEKLY -> base.plusWeeks(2);
            case MONTHLY -> base.plusMonths(1);
            case YEARLY -> base.plusYears(1);
        };
    }
    
    // Getters and Setters
    
    public Long getId() { 
        return id; 
    }
    
    public void setId(Long id) { 
        this.id = id; 
    }
    
    public String getDescription() { 
        return description; 
    }
    
    public void setDescription(String description) { 
        this.description = description; 
    }
    
    public BigDecimal getAmount() { 
        return amount; 
    }
    
    public void setAmount(BigDecimal amount) { 
        this.amount = amount; 
    }
    
    public Envelope getEnvelope() { 
        return envelope; 
    }
    
    public void setEnvelope(Envelope envelope) { 
        this.envelope = envelope; 
    }
    
    public RecurrencePattern getPattern() { 
        return pattern; 
    }
    
    public void setPattern(RecurrencePattern pattern) { 
        this.pattern = pattern; 
    }
    
    public LocalDateTime getNextDueDate() { 
        return nextDueDate; 
    }
    
    public void setNextDueDate(LocalDateTime nextDueDate) { 
        this.nextDueDate = nextDueDate; 
    }
    
    public LocalDateTime getLastProcessed() { 
        return lastProcessed; 
    }
    
    public void setLastProcessed(LocalDateTime lastProcessed) { 
        this.lastProcessed = lastProcessed; 
    }
    
    public Account getAccount() { 
        return account; 
    }
    
    public void setAccount(Account account) { 
        this.account = account; 
    }
    
    public TransactionType getType() { 
        return type; 
    }
    
    public void setType(TransactionType type) { 
        this.type = type; 
    }
}
