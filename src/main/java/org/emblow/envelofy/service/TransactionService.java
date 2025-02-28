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

import org.emblow.envelofy.domain.Transaction;
import org.emblow.envelofy.domain.TransactionType;
import org.emblow.envelofy.domain.Envelope;
import org.emblow.envelofy.domain.Account;
import org.emblow.envelofy.domain.InsufficientFundsException;
import org.emblow.envelofy.repository.TransactionRepository;
import org.emblow.envelofy.repository.EnvelopeRepository;
import org.emblow.envelofy.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.emblow.envelofy.domain.User;
import org.emblow.envelofy.exception.BusinessException;
import org.emblow.envelofy.exception.ValidationException;

// Update TransactionService.java
@Service
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final EnvelopeRepository envelopeRepository;
    private final AccountRepository accountRepository;
    private final SecurityService securityService;
    private final PatternService patternService;

    public TransactionService(
        TransactionRepository transactionRepository,
        EnvelopeRepository envelopeRepository,
        AccountRepository accountRepository,
        SecurityService securityService,
        PatternService patternService
    ) {
        this.transactionRepository = transactionRepository;
        this.envelopeRepository = envelopeRepository;
        this.accountRepository = accountRepository;
        this.securityService = securityService;
        this.patternService = patternService;
    }

    public List<Transaction> getRecentTransactions(LocalDateTime start, LocalDateTime end) {
        User currentUser = securityService.getCurrentUser();
        return transactionRepository.findByDateBetweenAndEnvelope_OwnerOrderByDateDesc(
            start, end, currentUser
        );
    }
    private void validateTransaction(
        LocalDateTime date,
        String description,
        BigDecimal amount,
        Long envelopeId,
        Long accountId
    ) {
        Map<String, String> violations = new HashMap<>();

        if (date == null) {
            violations.put("date", "Date is required");
        } else if (date.isAfter(LocalDateTime.now())) {
            violations.put("date", "Date cannot be in the future");
        }

        if (description == null || description.trim().isEmpty()) {
            violations.put("description", "Description is required");
        }

        if (amount == null) {
            violations.put("amount", "Amount is required");
        } else if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            violations.put("amount", "Amount must be positive");
        }

        if (envelopeId == null) {
            violations.put("envelopeId", "Envelope ID is required");
        }

        if (accountId == null) {
            violations.put("accountId", "Account ID is required");
        }

        if (!violations.isEmpty()) {
            throw new ValidationException(violations);
        }
    }
    @Transactional
    public Transaction recordTransaction(
        Long envelopeId,
        Long accountId,
        LocalDateTime date,
        String description,
        BigDecimal amount,
        TransactionType type
    ) {

        validateTransaction(date, description, amount, envelopeId, accountId);
        
    User currentUser = securityService.getCurrentUser();

    Envelope envelope = envelopeRepository.findByIdAndOwner(envelopeId, currentUser)
        .orElseThrow(() -> new BusinessException("ENVELOPE_NOT_FOUND", "Envelope not found"));
        
    Account account = accountRepository.findByIdAndOwner(accountId, currentUser)
        .orElseThrow(() -> new BusinessException("ACCOUNT_NOT_FOUND", "Account not found"));


        Transaction transaction = new Transaction(date, description, amount, envelope, account, type);

        if (null == type) {
            throw new IllegalArgumentException("Unsupported transaction type");
        } else switch (type) {
            case EXPENSE -> {
                BigDecimal accountAmount = account.getType() == Account.AccountType.CREDIT_CARD ?
                        amount : amount.negate();
                if (account.getType() != Account.AccountType.CREDIT_CARD &&
                        account.getBalance().subtract(amount).compareTo(BigDecimal.ZERO) < 0) {
                    throw new InsufficientFundsException(account.getName(), amount);
                }   envelope.spend(amount);
                account.debit(accountAmount);
            }
            case INCOME -> {
                envelope.allocate(amount);
                account.credit(amount);
            }
            default -> throw new IllegalArgumentException("Unsupported transaction type");
        }

        envelopeRepository.save(envelope);
        accountRepository.save(account);
        Transaction saved = transactionRepository.save(transaction);

        updatePatternLearning(saved);

        return saved;
    }

    @Transactional
    public Transaction updateTransaction(
        Long id,
        Long envelopeId,
        Long accountId,
        LocalDateTime date,
        String description,
        BigDecimal amount,
        TransactionType newType
    ) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        User currentUser = securityService.getCurrentUser();

        Transaction transaction = transactionRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Transaction not found"));
            
        // Verify ownership
        securityService.validateOwnership(transaction.getEnvelope().getOwner());

        Envelope oldEnvelope = transaction.getEnvelope();
        Account oldAccount = transaction.getAccount();
        BigDecimal oldAmount = transaction.getAmount();
        TransactionType oldType = transaction.getType();

        Envelope newEnvelope = envelopeRepository.findByIdAndOwner(envelopeId, currentUser)
            .orElseThrow(() -> new RuntimeException("Envelope not found"));
            
        Account newAccount = accountRepository.findByIdAndOwner(accountId, currentUser)
            .orElseThrow(() -> new RuntimeException("Account not found"));

        // Reverse old transaction effects
        if (oldType == TransactionType.EXPENSE) {
            oldEnvelope.unspend(oldAmount);
            BigDecimal oldAccountAmount = oldAccount.getType() == Account.AccountType.CREDIT_CARD ?
                oldAmount : oldAmount.negate();
            oldAccount.credit(oldAccountAmount);
        } else if (oldType == TransactionType.INCOME) {
            oldEnvelope.unallocate(oldAmount);
            oldAccount.debit(oldAmount);
        }

        if (null == newType) {
            throw new IllegalArgumentException("Unsupported transaction type");
        } else // Apply new transaction effects
        switch (newType) {
            case EXPENSE -> {
                newEnvelope.spend(amount);
                BigDecimal newAccountAmount = newAccount.getType() == Account.AccountType.CREDIT_CARD ?
                        amount : amount.negate();
                if (newAccount.getType() != Account.AccountType.CREDIT_CARD &&
                        newAccount.getBalance().subtract(amount).compareTo(BigDecimal.ZERO) < 0) {
                    throw new InsufficientFundsException(newAccount.getName(), amount);
                }   newAccount.debit(newAccountAmount);
            }
            case INCOME -> {
                newEnvelope.allocate(amount);
                newAccount.credit(amount);
            }
            default -> throw new IllegalArgumentException("Unsupported transaction type");
        }

        transaction.setDate(date);
        transaction.setDescription(description);
        transaction.setAmount(amount);
        transaction.setEnvelope(newEnvelope);
        transaction.setAccount(newAccount);
        transaction.setType(newType);

        envelopeRepository.save(oldEnvelope);
        if (!oldEnvelope.getId().equals(newEnvelope.getId())) {
            envelopeRepository.save(newEnvelope);
        }
        
        accountRepository.save(oldAccount);
        if (!oldAccount.getId().equals(newAccount.getId())) {
            accountRepository.save(newAccount);
        }

        Transaction updated = transactionRepository.save(transaction);

        if (!oldEnvelope.getId().equals(newEnvelope.getId())) {
            updatePatternLearning(updated);
        }

        return updated;
    }

    @Transactional
    public void deleteTransaction(Long id) {
        Transaction transaction = transactionRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Transaction not found"));
            
        // Verify ownership
        securityService.validateOwnership(transaction.getEnvelope().getOwner());

        Envelope envelope = transaction.getEnvelope();
        Account account = transaction.getAccount();
        BigDecimal amount = transaction.getAmount();
        TransactionType type = transaction.getType();

        if (type == TransactionType.EXPENSE) {
            envelope.unspend(amount);
            BigDecimal accountAmount = account.getType() == Account.AccountType.CREDIT_CARD ?
                amount : amount.negate();
            account.credit(accountAmount);
        } else if (type == TransactionType.INCOME) {
            envelope.unallocate(amount);
            account.debit(amount);
        }

        envelopeRepository.save(envelope);
        accountRepository.save(account);
        transactionRepository.delete(transaction);
    }

    private void updatePatternLearning(Transaction transaction) {
        try {
            patternService.suggestEnvelopes(transaction);
        } catch (Exception e) {
            System.err.println("Error updating pattern learning: " + e.getMessage());
        }
    }
}