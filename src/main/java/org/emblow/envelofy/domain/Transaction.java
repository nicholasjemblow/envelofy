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
import java.time.LocalDateTime;

@Entity
public class Transaction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    private LocalDateTime date;

    @NotNull
    private String description;

    @NotNull
    private BigDecimal amount;

    @ManyToOne
    @NotNull
    private Envelope envelope;

    @ManyToOne
    @NotNull
    private Account account;
 
    @NotNull
    @Enumerated(EnumType.STRING)
    private TransactionType type;
    
    // Default constructor for JPA
    public Transaction() {}

   public Transaction(LocalDateTime date, String description, BigDecimal amount, 
                       Envelope envelope, Account account, TransactionType type) {
        this.date = date;
        this.description = description;
        this.amount = amount;
        this.envelope = envelope;
        this.account = account;
        this.type = type;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDateTime getDate() { return date; }
    public void setDate(LocalDateTime date) { this.date = date; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public Envelope getEnvelope() { return envelope; }
    public void setEnvelope(Envelope envelope) { this.envelope = envelope; }

    // Helper method to get the category name (which is the envelope name)
    public String getCategory() {
        return envelope != null ? envelope.getName() : null;
    }
    public Account getAccount() { return account; }
    public void setAccount(Account account) { this.account = account; }
    public TransactionType getType() {
        return type;
    }
    public void setType(TransactionType type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return "Transaction{" +
                "id=" + id +
                ", type="+type +
                ", date=" + date +
                ", description='" + description + '\'' +
                ", amount=" + amount +
                ", envelope=" + (envelope != null ? envelope.getName() : "null") +
                '}';
    }
}