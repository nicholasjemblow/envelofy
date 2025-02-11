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
package org.emblow.envelopify.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.emblow.envelopify.domain.RecurringTransaction;
import org.emblow.envelopify.domain.Account;
import org.emblow.envelopify.repositry.RecurringTransactionRepository;
import org.emblow.envelopify.repositry.AccountRepository;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class RecurringTransactionService {
    private final RecurringTransactionRepository repository;
    private final TransactionService transactionService;
    private final AccountRepository accountRepository;

    public RecurringTransactionService(
        RecurringTransactionRepository repository,
        TransactionService transactionService,
        AccountRepository accountRepository
    ) {
        this.repository = repository;
        this.transactionService = transactionService;
        this.accountRepository = accountRepository;
    }

    @Transactional
    public RecurringTransaction create(RecurringTransaction recurring, Long accountId) {
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new RuntimeException("Account not found"));
        recurring.setAccount(account);
        recurring.setNextDueDate(recurring.calculateNextDueDate());
        return repository.save(recurring);
    }

    @Transactional
    public void update(Long id, RecurringTransaction updated, Long accountId) {
        RecurringTransaction existing = repository.findById(id)
            .orElseThrow(() -> new RuntimeException("Recurring transaction not found"));
            
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new RuntimeException("Account not found"));
            
        existing.setDescription(updated.getDescription());
        existing.setAmount(updated.getAmount());
        existing.setPattern(updated.getPattern());
        existing.setEnvelope(updated.getEnvelope());
        existing.setAccount(account);
        // Propagate the updated transaction type (e.g., INCOME or EXPENSE)
        existing.setType(updated.getType());
        
        repository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }

    public List<RecurringTransaction> getAllForEnvelope(Long envelopeId) {
        return repository.findByEnvelopeId(envelopeId);
    }

    @Scheduled(cron = "0 0 * * * *") // Run every hour
    @Transactional
    public void processRecurringTransactions() {
        LocalDateTime now = LocalDateTime.now();
        List<RecurringTransaction> due = repository.findByNextDueDateBefore(now);
        
        for (RecurringTransaction recurring : due) {
            try {
                // Create the actual transaction using the recurring transaction's type.
                // This smartly uses the type stored on the recurring transaction.
                transactionService.recordTransaction(
                    recurring.getEnvelope().getId(),
                    recurring.getAccount().getId(),
                    recurring.getNextDueDate(),
                    recurring.getDescription(),
                    recurring.getAmount(),
                    recurring.getType()
                );
                
                // Update the recurring transaction: mark it as processed and calculate the next due date.
                recurring.setLastProcessed(recurring.getNextDueDate());
                recurring.setNextDueDate(recurring.calculateNextDueDate());
                repository.save(recurring);
                
            } catch (Exception e) {
                // Log the error but continue processing other recurring transactions.
                System.err.println("Error processing recurring transaction: " + e.getMessage());
            }
        }
    }
}
