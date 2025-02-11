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

/**
 *
 * @author Nicholas J Emblow
 */
import org.emblow.envelopify.domain.Account;
import org.emblow.envelopify.repositry.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;

@Service
public class AccountService {
    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public List<Account> getAllAccounts() {
        return accountRepository.findAll();
    }

    public Account getAccount(Long id) {
        return accountRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Account not found"));
    }

    @Transactional
    public Account createAccount(String name, Account.AccountType type, 
                               String institution, String accountNumber) {
        Account account = new Account(name, type, institution);
        account.setAccountNumber(accountNumber);
        return accountRepository.save(account);
    }

    @Transactional
    public void updateBalance(Long accountId, BigDecimal amount, boolean isCredit) {
        Account account = getAccount(accountId);
        if (isCredit) {
            account.credit(amount);
        } else {
            account.debit(amount);
        }
        accountRepository.save(account);
    }

    @Transactional
    public void transferBetweenAccounts(Long sourceId, Long targetId, BigDecimal amount) {
        Account source = getAccount(sourceId);
        Account target = getAccount(targetId);
        
        source.debit(amount);
        target.credit(amount);
        
        accountRepository.save(source);
        accountRepository.save(target);
    }

    @Transactional
    public Account save(Account account) {
        return accountRepository.save(account);
    }

    @Transactional
    public void deleteAccount(Long id) {
        Account account = getAccount(id);
        if (!account.getTransactions().isEmpty()) {
            throw new IllegalStateException(
                "Cannot delete account that has transactions. " +
                "Transfer or delete transactions first."
            );
        }
        accountRepository.delete(account);
    }
}
