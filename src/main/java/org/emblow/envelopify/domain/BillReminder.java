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
package org.emblow.envelopify.domain;

/**
 *
 * @author Nicholas J Emblow
 */
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
public class BillReminder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotNull
    private String description;
    
    @NotNull
    private BigDecimal amount;
    
    @NotNull
    @ManyToOne
    private Envelope envelope;
    
    @NotNull
    @ManyToOne
    private Account account;
    
    @NotNull
    private LocalDate dueDate;
    
    private int reminderDays = 7;
    private boolean paid = false;
    
    // Default constructor
    public BillReminder() {}
    
    // Full constructor
    public BillReminder(
        String description,
        BigDecimal amount,
        Envelope envelope,
        Account account,
        LocalDate dueDate,
        int reminderDays
    ) {
        this.description = description;
        this.amount = amount;
        this.envelope = envelope;
        this.account = account;
        this.dueDate = dueDate;
        this.reminderDays = reminderDays;
    }
    
    // Add account getter/setter
    public Account getAccount() { return account; }
    public void setAccount(Account account) { this.account = account; }
    
    // Existing methods...
    public boolean isOverdue() {
        return !paid && LocalDate.now().isAfter(dueDate);
    }
    
    public boolean needsReminder() {
        if (paid) return false;
        LocalDate reminderDate = dueDate.minusDays(reminderDays);
        return LocalDate.now().isAfter(reminderDate) && !isOverdue();
    }
    
    // Rest of existing getters/setters...
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    
    public Envelope getEnvelope() { return envelope; }
    public void setEnvelope(Envelope envelope) { this.envelope = envelope; }
    
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    
    public int getReminderDays() { return reminderDays; }
    public void setReminderDays(int reminderDays) { this.reminderDays = reminderDays; }
    
    public boolean isPaid() { return paid; }
    public void setPaid(boolean paid) { this.paid = paid; }
}