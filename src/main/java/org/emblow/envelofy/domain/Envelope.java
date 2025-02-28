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

/**
 *
 * @author Nicholas J Emblow
 */
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Envelope {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotNull
    private String name;
    
    @NotNull
    private BigDecimal allocated = BigDecimal.ZERO;
    
    @NotNull
    private BigDecimal spent = BigDecimal.ZERO;
    
    @OneToMany(mappedBy = "envelope")
    private List<Transaction> transactions;

    @ManyToOne
    @NotNull
    private User owner;
        
    @NotNull
    private BigDecimal monthlyBudget = BigDecimal.ZERO;
    
    private LocalDate budgetResetDate = LocalDate.now().withDayOfMonth(1);
    
    @OneToMany(mappedBy = "envelope", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RecurringTransaction> recurringTransactions = new ArrayList<>();
    
    @OneToMany(mappedBy = "envelope", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BillReminder> billReminders = new ArrayList<>();
    
    // Constructors
    public Envelope() {}
    
    public Envelope(String name, BigDecimal allocated, User owner) {
        this.name = name;
        this.allocated = allocated;
        this.owner = owner;
    }
    
    // Smart methods
    public boolean canSpend(BigDecimal amount) {
        return getAvailable().compareTo(amount) >= 0;
    }
    
    
    public BigDecimal getAvailable() {
        return allocated.subtract(spent);
    }
    
    public void spend(BigDecimal amount) {
        if (!canSpend(amount)) {
            throw new InsufficientFundsException(name, amount);
        }
        spent = spent.add(amount);
    }
    
    public void unspend(BigDecimal amount) {
        if (!canSpend(amount)) {
            throw new InsufficientFundsException(name, amount);
        }
        spent = spent.subtract(amount);
    }
    
    public void unallocate(BigDecimal amount) {
        this.allocated = this.allocated.subtract(amount);
    }
    
    public void allocate(BigDecimal amount) {
        this.allocated = this.allocated.add(amount);
    }
    
    public void withdraw(BigDecimal amount) {
        if (amount.compareTo(getAvailable()) > 0) {
            throw new InsufficientFundsException(name, amount);
        }
        this.allocated = this.allocated.subtract(amount);
    }
    
    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getAllocated() { return allocated; }
    public BigDecimal getSpent() { return spent; }
    public List<Transaction> getTransactions() { return transactions; }
    public void setTransactions(List<Transaction> transactions) { 
        this.transactions = transactions; 
    }
    
    public double getSpentPercentage() {
        if (allocated.compareTo(BigDecimal.ZERO) == 0) return 0.0;
        return spent.divide(allocated, 4, BigDecimal.ROUND_HALF_UP)
                   .multiply(BigDecimal.valueOf(100))
                   .doubleValue();
    }
    
    public void setMonthlyBudget(BigDecimal amount) {
        this.monthlyBudget = amount;
    }
    
    public BigDecimal getMonthlyBudget() {
        return monthlyBudget;
    }
    
    public double getBudgetUtilization() {
        if (monthlyBudget.compareTo(BigDecimal.ZERO) == 0) return 0.0;
        return getCurrentMonthSpent()
            .divide(monthlyBudget, 4, RoundingMode.HALF_UP)
            .doubleValue();
    }
    
    public BigDecimal getCurrentMonthSpent() {
        return transactions.stream()
            .filter(tx -> tx.getDate().isAfter(budgetResetDate.atStartOfDay()))
            .map(Transaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    public BigDecimal getRemainingBudget() {
        return monthlyBudget.subtract(getCurrentMonthSpent());
    }  
    
    @OneToOne(mappedBy = "envelope")
    private Category category;
    
    // Add getter/setter
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    
    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }

}