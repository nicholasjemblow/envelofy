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

import org.emblow.envelofy.domain.Account;
import org.emblow.envelofy.domain.User;
import org.emblow.envelofy.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.emblow.envelofy.exception.AccountException;
import org.emblow.envelofy.exception.BusinessException;
import org.emblow.envelofy.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;

@Service
public class AccountService {
    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository accountRepository;
    private final SecurityService securityService;

    public AccountService(AccountRepository accountRepository, SecurityService securityService) {
        this.accountRepository = accountRepository;
        this.securityService = securityService;
    }

    public List<Account> getAllAccounts() {
        User currentUser = securityService.getCurrentUser();
        return accountRepository.findByOwner(currentUser);
    }

    @Transactional(readOnly = true)
    public Account getAccount(Long id) {
        User currentUser = securityService.getCurrentUser();

        return accountRepository.findByIdWithTransactions(id)
            .filter(account -> account.getOwner().getId().equals(currentUser.getId()))
            .orElseThrow(() -> new AccountException(
                AccountException.ACCOUNT_NOT_FOUND,
                String.format("Account %d not found or access denied", id)
            ));
    }
   
    @Transactional
    public Account createAccount(String name, Account.AccountType type, 
                               String institution, String accountNumber) {
        validateAccountCreation(name, type, institution, accountNumber);
        
        User currentUser = securityService.getCurrentUser();
        Account account = new Account(name, type, institution, currentUser);
        account.setAccountNumber(accountNumber);
        
        try {
            return accountRepository.save(account);
        } catch (DataIntegrityViolationException e) {
            throw new AccountException(AccountException.DUPLICATE_ACCOUNT, 
                "An account with this name already exists");
        }
    }
    private void validateAccountCreation(String name, Account.AccountType type, 
                                      String institution, String accountNumber) {
        Map<String, String> violations = new HashMap<>();
        
        if (name == null || name.trim().isEmpty()) {
            violations.put("name", "Name is required");
        } else if (name.length() > 100) {
            violations.put("name", "Name must be less than 100 characters");
        }
        
        if (type == null) {
            violations.put("type", "Account type is required");
        }
        
        if (accountNumber != null && !accountNumber.matches("\\d{4}")) {
            violations.put("accountNumber", "Account number must be exactly 4 digits");
        }
        
        if (!violations.isEmpty()) {
            throw new ValidationException(violations);
        }
    }
    
    @Transactional
    public void updateBalance(Long accountId, BigDecimal amount, boolean isCredit) {
        validateBalanceUpdate(accountId, amount);
        
        Account account = getAccount(accountId);
        
        if (!isCredit && account.getBalance().subtract(amount).compareTo(BigDecimal.ZERO) < 0 
            && account.getType() != Account.AccountType.CREDIT_CARD) {
            throw new AccountException(AccountException.INSUFFICIENT_BALANCE,
                String.format("Insufficient balance in account %s: required %.2f, available %.2f",
                    account.getName(), amount, account.getBalance()));
        }
        
        if (isCredit) {
            account.credit(amount);
        } else {
            account.debit(amount);
        }
        
        accountRepository.save(account);
    }
    
    private void validateBalanceUpdate(Long accountId, BigDecimal amount) {
        Map<String, String> violations = new HashMap<>();
        
        if (accountId == null) {
            violations.put("accountId", "Account ID is required");
        }
        
        if (amount == null) {
            violations.put("amount", "Amount is required");
        } else if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            violations.put("amount", "Amount must be positive");
        }
        
        if (!violations.isEmpty()) {
            throw new ValidationException(violations);
        }
    }
    
    @Transactional
    public void transferBetweenAccounts(Long sourceId, Long targetId, BigDecimal amount) {
        validateTransfer(sourceId, targetId, amount);
        
        Account source = getAccount(sourceId);
        Account target = getAccount(targetId);
        
        if (sourceId.equals(targetId)) {
            throw new BusinessException("INVALID_OPERATION", 
                "Cannot transfer to the same account");
        }

        if (source.getBalance().subtract(amount).compareTo(BigDecimal.ZERO) < 0 
            && source.getType() != Account.AccountType.CREDIT_CARD) {
            throw new AccountException(AccountException.INSUFFICIENT_BALANCE,
                String.format("Insufficient balance in account %s: required %.2f, available %.2f",
                    source.getName(), amount, source.getBalance()));
        }
        
        source.debit(amount);
        target.credit(amount);
        
        accountRepository.save(source);
        accountRepository.save(target);
    }
    
    private void validateTransfer(Long sourceId, Long targetId, BigDecimal amount) {
        Map<String, String> violations = new HashMap<>();
        
        if (sourceId == null) {
            violations.put("sourceId", "Source account ID is required");
        }
        
        if (targetId == null) {
            violations.put("targetId", "Target account ID is required");
        }
        
        if (amount == null) {
            violations.put("amount", "Amount is required");
        } else if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            violations.put("amount", "Amount must be positive");
        }
        
        if (!violations.isEmpty()) {
            throw new ValidationException(violations);
        }
    }
    
    /**
     * Saves the given account. If the account already exists, it validates that
     * the current user is the owner before saving the changes.
     */
    @Transactional
    public Account save(Account account) {
        // If the account has an ID, assume it's an update and validate ownership.
        if (account.getId() != null) {
            securityService.validateOwnership(account.getOwner());
        } else {
            // For a new account, enforce that the owner is the current user.
            User currentUser = securityService.getCurrentUser();
            if (!currentUser.getId().equals(account.getOwner().getId())) {
                throw new RuntimeException("Cannot assign account owner to another user.");
            }
        }
        return accountRepository.save(account);
    }

    @Transactional
    public void deleteAccount(Long id) {
        Account account = getAccount(id);
        
        if (!account.getTransactions().isEmpty()) {
            throw new BusinessException("ACCOUNT_IN_USE",
                "Cannot delete account that has transactions. " +
                "Transfer or delete transactions first.");
        }
        
        accountRepository.delete(account);
    }
}
