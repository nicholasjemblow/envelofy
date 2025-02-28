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

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.emblow.envelofy.domain.RecurringTransaction;
import org.emblow.envelofy.domain.Account;
import org.emblow.envelofy.repository.RecurringTransactionRepository;
import org.emblow.envelofy.repository.AccountRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.emblow.envelofy.domain.Envelope;
import org.emblow.envelofy.domain.TransactionType;
import org.emblow.envelofy.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class RecurringTransactionService {
    private static final Logger log = LoggerFactory.getLogger(RecurringTransactionService.class);
    
    private final RecurringTransactionRepository repository;
    private final TransactionService transactionService;
    private final SecurityService securityService;
    private final EnvelopeService envelopeService;
    private final AccountService accountService;

    public RecurringTransactionService(
        RecurringTransactionRepository repository,
        TransactionService transactionService,
        SecurityService securityService,
        EnvelopeService envelopeService,
        AccountService accountService
    ) {
        this.repository = repository;
        this.transactionService = transactionService;
        this.securityService = securityService;
        this.envelopeService = envelopeService;
        this.accountService = accountService;
    }

    @Transactional
    public RecurringTransaction create(RecurringTransaction recurring, Long accountId) {
        try {
            // Validate ownership of both envelope and account
            Envelope envelope = envelopeService.getEnvelope(recurring.getEnvelope().getId());
            Account account = accountService.getAccount(accountId);
            
            recurring.setAccount(account);
            recurring.setEnvelope(envelope);
            recurring.setNextDueDate(recurring.calculateNextDueDate());
            
            return repository.save(recurring);
            
        } catch (Exception e) {
            log.error("Error creating recurring transaction", e);
            throw new RuntimeException("Could not create recurring transaction: " + e.getMessage());
        }
    }

    @Transactional
    public void update(Long id, RecurringTransaction updated, Long accountId) {
        try {
            RecurringTransaction existing = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Recurring transaction not found"));
                
            // Validate ownership
            Envelope envelope = envelopeService.getEnvelope(updated.getEnvelope().getId());
            Account account = accountService.getAccount(accountId);
            
            // Update fields
            existing.setDescription(updated.getDescription());
            existing.setAmount(updated.getAmount());
            existing.setPattern(updated.getPattern());
            existing.setEnvelope(envelope);
            existing.setAccount(account);
            existing.setType(updated.getType());
            
            repository.save(existing);
            
        } catch (Exception e) {
            log.error("Error updating recurring transaction", e);
            throw new RuntimeException("Could not update recurring transaction: " + e.getMessage());
        }
    }

    @Transactional
    public void delete(Long id) {
        try {
            RecurringTransaction recurring = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Recurring transaction not found"));
                
            // Validate ownership through envelope
            envelopeService.getEnvelope(recurring.getEnvelope().getId());
            repository.deleteById(id);
            
        } catch (Exception e) {
            log.error("Error deleting recurring transaction", e);
            throw new RuntimeException("Could not delete recurring transaction: " + e.getMessage());
        }
    }

    public List<RecurringTransaction> getAllForEnvelope(Long envelopeId) {
        try {
            // This validates ownership
            envelopeService.getEnvelope(envelopeId);
            return repository.findByEnvelopeId(envelopeId);
            
        } catch (Exception e) {
            log.error("Error retrieving recurring transactions for envelope", e);
            throw new RuntimeException("Could not retrieve recurring transactions: " + e.getMessage());
        }
    }

    public List<RecurringTransaction> getAllForAccount(Long accountId) {
        try {
            // This validates ownership
            accountService.getAccount(accountId);
            return repository.findByAccountId(accountId);
            
        } catch (Exception e) {
            log.error("Error retrieving recurring transactions for account", e);
            throw new RuntimeException("Could not retrieve recurring transactions: " + e.getMessage());
        }
    }

    @Scheduled(cron = "0 0 * * * *") // Run every hour
    @Transactional
    public void processRecurringTransactions() {
        try {
            LocalDateTime now = LocalDateTime.now();
            List<RecurringTransaction> due = repository.findByNextDueDateBefore(now);
            
            for (RecurringTransaction recurring : due) {
                try {
                    // Validate ownership through envelope
                    envelopeService.getEnvelope(recurring.getEnvelope().getId());
                    
                    // Record the transaction
                    transactionService.recordTransaction(
                        recurring.getEnvelope().getId(),
                        recurring.getAccount().getId(),
                        recurring.getNextDueDate(),
                        recurring.getDescription(),
                        recurring.getAmount(),
                        recurring.getType()
                    );
                    
                    // Update the recurring transaction
                    recurring.setLastProcessed(recurring.getNextDueDate());
                    recurring.setNextDueDate(recurring.calculateNextDueDate());
                    repository.save(recurring);
                    
                    log.info("Processed recurring transaction: {} for amount: {}", 
                        recurring.getDescription(), 
                        recurring.getAmount());
                        
                } catch (Exception e) {
                    log.error("Error processing recurring transaction: {}", recurring.getId(), e);
                }
            }
        } catch (Exception e) {
            log.error("Error in recurring transaction processor", e);
        }
    }

    public List<RecurringTransaction> getByType(TransactionType type) {
        try {
            User currentUser = securityService.getCurrentUser();
            return repository.findByType(type).stream()
                .filter(tx -> tx.getEnvelope().getOwner().getId().equals(currentUser.getId()))
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("Error retrieving recurring transactions by type", e);
            throw new RuntimeException("Could not retrieve recurring transactions: " + e.getMessage());
        }
    }

    public List<RecurringTransaction> getByAccountAndType(Long accountId, TransactionType type) {
        try {
            // Validate account ownership
            accountService.getAccount(accountId);
            return repository.findByAccountIdAndType(accountId, type);
            
        } catch (Exception e) {
            log.error("Error retrieving recurring transactions by account and type", e);
            throw new RuntimeException("Could not retrieve recurring transactions: " + e.getMessage());
        }
    }
}
