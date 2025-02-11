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

import org.emblow.envelopify.domain.Transaction;
import org.emblow.envelopify.domain.TransactionType;
import org.emblow.envelopify.domain.Envelope;
import org.emblow.envelopify.domain.Account;
import org.emblow.envelopify.domain.InsufficientFundsException;
import org.emblow.envelopify.repositry.TransactionRepository;
import org.emblow.envelopify.repositry.EnvelopeRepository;
import org.emblow.envelopify.repositry.AccountRepository;
import org.emblow.envelopify.service.PatternService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final EnvelopeRepository envelopeRepository;
    private final AccountRepository accountRepository;
    private final PatternService patternService;

    public TransactionService(
            TransactionRepository transactionRepository,
            EnvelopeRepository envelopeRepository,
            AccountRepository accountRepository,
            PatternService patternService
    ) {
        this.transactionRepository = transactionRepository;
        this.envelopeRepository = envelopeRepository;
        this.accountRepository = accountRepository;
        this.patternService = patternService;
    }
    public List<Transaction> getRecentTransactions(LocalDateTime start, LocalDateTime end) {
        return transactionRepository.findByDateBetweenOrderByDateDesc(start, end);
    }
    /**
     * Records a new transaction. Depending on the transaction type, the method updates the
     * account and envelope balances accordingly.
     *
     * @param envelopeId  the ID of the envelope to which the transaction applies
     * @param accountId   the ID of the account from which the transaction is recorded
     * @param date        the date/time of the transaction
     * @param description a brief description of the transaction
     * @param amount      the transaction amount (must be positive)
     * @param type        the transaction type (INCOME or EXPENSE)
     * @return the saved Transaction object
     */
    @Transactional
    public Transaction recordTransaction(
            Long envelopeId,
            Long accountId,
            LocalDateTime date,
            String description,
            BigDecimal amount,
            TransactionType type
    ) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        Envelope envelope = envelopeRepository.findById(envelopeId)
                .orElseThrow(() -> new RuntimeException("Envelope not found"));
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        // Create a new transaction with the given type.
        Transaction transaction = new Transaction(date, description, amount, envelope, account, type);

        if (type == TransactionType.EXPENSE) {
            // For expenses, if the account is a credit card, the amount is used as is;
            // otherwise, subtract the amount from the account balance.
            BigDecimal accountAmount = account.getType() == Account.AccountType.CREDIT_CARD ?
                    amount : amount.negate();
            // Check sufficient funds for non-credit accounts.
            if (account.getType() != Account.AccountType.CREDIT_CARD &&
                    account.getBalance().subtract(amount).compareTo(BigDecimal.ZERO) < 0) {
                throw new InsufficientFundsException(account.getName(), amount);
            }
            envelope.spend(amount);
            account.debit(accountAmount);
        } else if (type == TransactionType.INCOME) {
            // For income transactions, add funds to both the envelope and the account.
            envelope.allocate(amount);
            account.credit(amount);
        } else {
            throw new IllegalArgumentException("Unsupported transaction type");
        }

        // Save the updated envelope and account.
        envelopeRepository.save(envelope);
        accountRepository.save(account);
        Transaction saved = transactionRepository.save(transaction);

        // Update any pattern learning based on the new transaction.
        updatePatternLearning(saved);

        return saved;
    }

    /**
     * Updates an existing transaction. This method first reverses the effects of the old
     * transaction (based on its type) and then applies the new values and effects.
     *
     * @param id          the ID of the transaction to update
     * @param envelopeId  the new envelope ID
     * @param accountId   the new account ID
     * @param date        the new transaction date/time
     * @param description the new description
     * @param amount      the new amount (must be positive)
     * @param newType     the new transaction type (INCOME or EXPENSE)
     * @return the updated Transaction object
     */
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

        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        // Save old state for reversal.
        Envelope oldEnvelope = transaction.getEnvelope();
        Account oldAccount = transaction.getAccount();
        BigDecimal oldAmount = transaction.getAmount();
        TransactionType oldType = transaction.getType();

        // Look up the new envelope and account.
        Envelope newEnvelope = envelopeRepository.findById(envelopeId)
                .orElseThrow(() -> new RuntimeException("Envelope not found"));
        Account newAccount = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        // Reverse the effects of the old transaction.
        if (oldType == TransactionType.EXPENSE) {
            oldEnvelope.unspend(oldAmount);
            BigDecimal oldAccountAmount = oldAccount.getType() == Account.AccountType.CREDIT_CARD ?
                    oldAmount : oldAmount.negate();
            oldAccount.credit(oldAccountAmount);
        } else if (oldType == TransactionType.INCOME) {
            oldEnvelope.unallocate(oldAmount);
            oldAccount.debit(oldAmount);
        }

        // Apply the new transaction effects.
        if (newType == TransactionType.EXPENSE) {
            newEnvelope.spend(amount);
            BigDecimal newAccountAmount = newAccount.getType() == Account.AccountType.CREDIT_CARD ?
                    amount : amount.negate();
            if (newAccount.getType() != Account.AccountType.CREDIT_CARD &&
                    newAccount.getBalance().subtract(amount).compareTo(BigDecimal.ZERO) < 0) {
                throw new InsufficientFundsException(newAccount.getName(), amount);
            }
            newAccount.debit(newAccountAmount);
        } else if (newType == TransactionType.INCOME) {
            newEnvelope.allocate(amount);
            newAccount.credit(amount);
        } else {
            throw new IllegalArgumentException("Unsupported transaction type");
        }

        // Update the transaction details.
        transaction.setDate(date);
        transaction.setDescription(description);
        transaction.setAmount(amount);
        transaction.setEnvelope(newEnvelope);
        transaction.setAccount(newAccount);
        transaction.setType(newType);

        // Save changes to the envelopes and accounts.
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

    /**
     * Deletes a transaction by reversing its effects on the envelope and account balances,
     * then removing it from the repository.
     *
     * @param id the ID of the transaction to delete
     */
    @Transactional
    public void deleteTransaction(Long id) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

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

    /**
     * Helper method to update pattern learning. Exceptions are logged but do not interrupt
     * transaction processing.
     *
     * @param transaction the transaction to use for updating patterns
     */
    private void updatePatternLearning(Transaction transaction) {
        try {
            patternService.suggestEnvelopes(transaction);
        } catch (Exception e) {
            System.err.println("Error updating pattern learning: " + e.getMessage());
        }
    }
}
