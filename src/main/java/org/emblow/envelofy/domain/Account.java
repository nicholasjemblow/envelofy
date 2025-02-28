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
import java.util.ArrayList;
import java.util.List;

@Entity
public class Account {
    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotNull
    private String name;
    
    @NotNull
    private AccountType type;
    
    @NotNull
    private BigDecimal balance = BigDecimal.ZERO;
    

    @ManyToOne
    @NotNull
    private User owner;    
    
    private String accountNumber; // Last 4 digits for reference
    private String institution;
     @OneToMany(mappedBy = "account")
    private List<RecurringTransaction> recurringTransactions = new ArrayList<>();   
    @OneToMany(mappedBy = "account")
    private List<Transaction> transactions = new ArrayList<>();
    
    public enum AccountType {
        CHECKING("Checking"),
        SAVINGS("Savings"),
        CREDIT_CARD("Credit Card"),
        CASH("Cash"),
        INVESTMENT("Investment");
        
        private final String displayName;
        
        AccountType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }

    // Constructors
    public Account() {}
    
    public Account(String name, AccountType type, String institution, User owner) {
        this.name = name;
        this.type = type;
        this.institution = institution;
        this.owner = owner;
    }
    
    // Balance management methods
    public void credit(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }
    
    public void debit(BigDecimal amount) {
        this.balance = this.balance.subtract(amount);
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public AccountType getType() { return type; }
    public void setType(AccountType type) { this.type = type; }
    
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
    
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    
    public String getInstitution() { return institution; }
    public void setInstitution(String institution) { this.institution = institution; }
    
    public List<Transaction> getTransactions() { return transactions; }
    public void setTransactions(List<Transaction> transactions) { this.transactions = transactions; }

    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }
}
