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

import org.emblow.envelopify.domain.Envelope;
import org.emblow.envelopify.domain.Account;
import org.emblow.envelopify.domain.Transaction;
import org.emblow.envelopify.domain.TransactionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class TestDataService {
    private static final Logger log = LoggerFactory.getLogger(TestDataService.class);
    
    private final EnvelopeService envelopeService;
    private final AccountService accountService;
    private final TransactionService transactionService;
    private final Random random = new Random();

    public TestDataService(
        EnvelopeService envelopeService,
        AccountService accountService,
        TransactionService transactionService
    ) {
        this.envelopeService = envelopeService;
        this.accountService = accountService;
        this.transactionService = transactionService;
    }

    /**
     * Creates test data in several steps:
     * 
     * 1. Create the accounts.
     * 2. Create the envelopes (for expense categories) with an initial allocation of zero.
     * 3. For each month in the past 6 months, simulate a transfer from the checking account
     *    into each envelope (i.e. the monthly allocation) and then record expense transactions
     *    against the envelopes.
     * 4. Record a few unusual one‑off transactions.
     */
    @Transactional
    public void createTestData() {
        // --- Step 1: Create accounts ---
        Account checking = accountService.createAccount(
            "Main Checking",
            Account.AccountType.CHECKING,
            "Chase Bank",
            "1234"
        );
        
        Account savings = accountService.createAccount(
            "Emergency Fund",
            Account.AccountType.SAVINGS,
            "Ally Bank",
            "5678"
        );
        
        Account creditCard = accountService.createAccount(
            "Rewards Card",
            Account.AccountType.CREDIT_CARD,
            "Capital One",
            "9012"
        );
        
        // --- Step 2: Create envelopes for expense categories.
        // (We create them with 0 initial funds; funds will be added monthly.)
        Envelope rent = envelopeService.createEnvelope("Rent", BigDecimal.ZERO);
        Envelope utilities = envelopeService.createEnvelope("Utilities", BigDecimal.ZERO);
        Envelope groceries = envelopeService.createEnvelope("Groceries", BigDecimal.ZERO);
        Envelope entertainment = envelopeService.createEnvelope("Entertainment", BigDecimal.ZERO);
        Envelope transport = envelopeService.createEnvelope("Transport", BigDecimal.ZERO);
        envelopeService.flush();
        
        // --- Step 3: For each of the past 6 months, simulate monthly transfers and expense transactions.
        LocalDateTime now = LocalDateTime.now();
        // Define monthly allocation amounts for each envelope.
        BigDecimal rentAllocation = new BigDecimal("2000.00");
        BigDecimal utilitiesAllocation = new BigDecimal("300.00");
        BigDecimal groceriesAllocation = new BigDecimal("600.00");
        BigDecimal entertainmentAllocation = new BigDecimal("200.00");
        BigDecimal transportAllocation = new BigDecimal("150.00");
        
        // Loop over the past 6 months (month = 5 is the oldest, month = 0 is the current month)
        for (int month = 5; month >= 0; month--) {
            // Determine a transfer date for this month (e.g., the first day)
            LocalDateTime transferDate = now.minusMonths(month).withDayOfMonth(1);
            
            // Simulate a transfer into each envelope from the checking account.
            createTransaction(rent.getId(), checking.getId(), transferDate,
                "Monthly Rent Transfer", rentAllocation, TransactionType.INCOME);
            createTransaction(utilities.getId(), checking.getId(), transferDate,
                "Monthly Utilities Transfer", utilitiesAllocation, TransactionType.INCOME);
            createTransaction(groceries.getId(), checking.getId(), transferDate,
                "Monthly Groceries Transfer", groceriesAllocation, TransactionType.INCOME);
            createTransaction(entertainment.getId(), checking.getId(), transferDate,
                "Monthly Entertainment Transfer", entertainmentAllocation, TransactionType.INCOME);
            createTransaction(transport.getId(), checking.getId(), transferDate,
                "Monthly Transport Transfer", transportAllocation, TransactionType.INCOME);
            envelopeService.flush();
            
            // Now record expense transactions for this month.
            // Rent: one expense on the transfer date.
            createTransaction(rent.getId(), checking.getId(), transferDate,
                "Monthly Rent Payment", new BigDecimal("1800.00"), TransactionType.EXPENSE);
            
            // Utilities: expense on the 15th of the month.
            LocalDateTime utilitiesExpenseDate = now.minusMonths(month).withDayOfMonth(15);
            int monthValue = utilitiesExpenseDate.getMonthValue();
            BigDecimal utilitiesExpense = (monthValue <= 3 || monthValue >= 11)
                ? new BigDecimal("280.00")
                : new BigDecimal("180.00");
            createTransaction(utilities.getId(), checking.getId(), utilitiesExpenseDate,
                "Power and Water Bill", utilitiesExpense, TransactionType.EXPENSE);
            
            // Groceries: simulate roughly 4 weekly expenses per month.
            for (int week = 0; week < 4; week++) {
                LocalDateTime weekDate = transferDate.plusDays(week * 7);
                // Randomly choose between checking and credit card.
                Account selectedAccount = random.nextBoolean() ? checking : creditCard;
                createTransaction(groceries.getId(), selectedAccount.getId(), weekDate,
                    "Weekly Groceries", randomAmount(120, 160), TransactionType.EXPENSE);
            }
            
            // Entertainment: simulate 1 to 3 random expense events within the month on the credit card.
            int entertainmentCount = random.nextInt(3) + 1;
            for (int j = 0; j < entertainmentCount; j++) {
                LocalDateTime entDate = transferDate.plusDays(random.nextInt(28));
                createTransaction(entertainment.getId(), creditCard.getId(), entDate,
                    randomEntertainmentDescription(), randomAmount(20, 80), TransactionType.EXPENSE);
            }
            
            // Transport: simulate a monthly bus pass expense and a couple of rideshare expenses.
            createTransaction(transport.getId(), checking.getId(), transferDate,
                "Monthly Bus Pass", new BigDecimal("60.00"), TransactionType.EXPENSE);
            for (int j = 0; j < 2; j++) {
                LocalDateTime rideDate = transferDate.plusDays(random.nextInt(28));
                createTransaction(transport.getId(), creditCard.getId(), rideDate,
                    "Uber Ride", randomAmount(15, 35), TransactionType.EXPENSE);
            }
            
            envelopeService.flush();
        }
        
        // --- Step 4: Record a few unusual one‑off expense transactions.
        createTransaction(groceries.getId(), creditCard.getId(), now.minusDays(5),
            "Big Party Shopping", new BigDecimal("450.00"), TransactionType.EXPENSE);
        createTransaction(entertainment.getId(), creditCard.getId(), now.minusDays(10),
            "Concert Tickets", new BigDecimal("180.00"), TransactionType.EXPENSE);
        envelopeService.flush();
    }
    
    /**
     * Creates a transaction in its own transaction boundary.
     * For expense transactions, it verifies that the envelope has sufficient funds and deducts the amount;
     * for income transactions, it increases the envelope’s allocation.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private boolean createTransaction(
        Long envelopeId,
        Long accountId,
        LocalDateTime date,
        String description,
        BigDecimal amount,
        TransactionType type
    ) {
        Envelope envelope;
        try {
            envelope = envelopeService.getEnvelope(envelopeId);
        } catch (RuntimeException ex) {
            log.error("Failed to retrieve envelope with id " + envelopeId, ex);
            envelope = null;
        }
        if (envelope == null) {
            log.error("Failed to create test transaction '{}': Envelope not found", description);
            return false;
        }
        
        if (type == TransactionType.EXPENSE) {
            if (!envelope.canSpend(amount)) {
                log.error("Failed to create test transaction '{}': Insufficient funds in envelope '{}' for amount: {}",
                    description, envelope.getName(), amount);
                return false;
            }
            envelope.spend(amount);
        } else if (type == TransactionType.INCOME) {
            envelope.allocate(amount);
        }
        
        try {
            transactionService.recordTransaction(envelopeId, accountId, date, description, amount, type);
            log.debug("Created test transaction: {} for ${}", description, amount);
            return true;
        } catch (Exception e) {
            log.warn("Failed to create test transaction: {} - {}", description, e.getMessage());
            return false;
        }
    }
    
    private BigDecimal randomAmount(double min, double max) {
        double amt = min + (random.nextDouble() * (max - min));
        return BigDecimal.valueOf(amt).setScale(2, BigDecimal.ROUND_HALF_UP);
    }
    
    private String randomEntertainmentDescription() {
        String[] options = {
            "Movie Tickets",
            "Restaurant Dinner",
            "Bowling Night",
            "Board Game Cafe",
            "Bar Tab",
            "Coffee and Dessert",
            "Arcade Games",
            "Mini Golf"
        };
        return options[random.nextInt(options.length)];
    }
}
