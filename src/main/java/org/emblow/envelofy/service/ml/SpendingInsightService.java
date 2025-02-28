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
package org.emblow.envelofy.service.ml;

/**
 *
 * @author Nicholas J Emblow
 */
import org.springframework.stereotype.Service;
import org.emblow.envelofy.domain.Transaction;
import org.emblow.envelofy.domain.Envelope;
import org.emblow.envelofy.domain.Account;
import org.emblow.envelofy.repository.TransactionRepository;
import org.emblow.envelofy.repository.AccountRepository;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class SpendingInsightService {
    private static final Logger log = LoggerFactory.getLogger(SpendingInsightService.class);
    
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final TransactionClassifier classifier;

    public SpendingInsightService(
        TransactionRepository transactionRepository,
        AccountRepository accountRepository,
        TransactionClassifier classifier
    ) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.classifier = classifier;
    }

    public List<SpendingInsight> generateInsights() {
        List<SpendingInsight> insights = new ArrayList<>();
        
        try {
            // Add different types of insights
            insights.addAll(detectRecurringPayments());
            insights.addAll(detectUnusualSpending());
            insights.addAll(predictUpcomingExpenses());
            insights.addAll(generateBudgetSuggestions());
            insights.addAll(generateAccountSpecificInsights());
            insights.addAll(detectCrossAccountPatterns());
            
            log.debug("Generated {} insights", insights.size());
        } catch (Exception e) {
            log.error("Error generating insights", e);
        }
        
        return insights;
    }

    private List<SpendingInsight> detectRecurringPayments() {
        List<SpendingInsight> insights = new ArrayList<>();
        
        // Get last 6 months of transactions
        LocalDateTime sixMonthsAgo = LocalDateTime.now().minusMonths(6);
        List<Transaction> transactions = transactionRepository
            .findByDateAfterOrderByDateDesc(sixMonthsAgo);

        // Group by account and envelope
        Map<Account, Map<Envelope, List<Transaction>>> groupedTransactions = transactions.stream()
            .collect(Collectors.groupingBy(
                Transaction::getAccount,
                Collectors.groupingBy(Transaction::getEnvelope)
            ));

        // Analyze each account's envelopes
        for (Map.Entry<Account, Map<Envelope, List<Transaction>>> accountEntry : 
             groupedTransactions.entrySet()) {
            
            Account account = accountEntry.getKey();
            
            for (Map.Entry<Envelope, List<Transaction>> envEntry : 
                 accountEntry.getValue().entrySet()) {
                
                Envelope envelope = envEntry.getKey();
                List<Transaction> envTransactions = envEntry.getValue();
                
                // Look for regular patterns in amounts and timing
                Map<BigDecimal, List<Transaction>> byAmount = envTransactions.stream()
                    .collect(Collectors.groupingBy(Transaction::getAmount));

                // Analyze each amount group for timing patterns
                for (Map.Entry<BigDecimal, List<Transaction>> amountGroup : 
                     byAmount.entrySet()) {
                    
                    if (amountGroup.getValue().size() >= 3) {
                        OptionalDouble avgDays = calculateAverageDaysBetween(
                            amountGroup.getValue()
                        );
                        
                        if (avgDays.isPresent()) {
                            double stdDev = calculateStdDev(
                                amountGroup.getValue(), 
                                avgDays.getAsDouble()
                            );
                            
                            if (stdDev < 5.0) {
                                insights.add(new SpendingInsight(
                                    SpendingInsightType.RECURRING_PAYMENT,
                                    String.format(
                                        "Recurring payment detected in %s using %s: $%.2f every %.1f days",
                                        envelope.getName(),
                                        account.getName(),
                                        amountGroup.getKey(),
                                        avgDays.getAsDouble()
                                    ),
                                    0.9
                                ));
                            }
                        }
                    }
                }
            }
        }

        return insights;
    }

    private List<SpendingInsight> detectUnusualSpending() {
        List<SpendingInsight> insights = new ArrayList<>();
        
        // Get historical spending patterns
        LocalDateTime threeMonthsAgo = LocalDateTime.now().minusMonths(3);
        List<Transaction> recentTransactions = transactionRepository
            .findByDateAfterOrderByDateDesc(threeMonthsAgo);

        // Group by account and envelope
        Map<Account, Map<Envelope, List<Transaction>>> groupedTransactions = recentTransactions.stream()
            .collect(Collectors.groupingBy(
                Transaction::getAccount,
                Collectors.groupingBy(Transaction::getEnvelope)
            ));

        // Analyze each account's envelopes
        for (Map.Entry<Account, Map<Envelope, List<Transaction>>> accountEntry : 
             groupedTransactions.entrySet()) {
            
            Account account = accountEntry.getKey();
            
            for (Map.Entry<Envelope, List<Transaction>> envEntry : 
                 accountEntry.getValue().entrySet()) {
                
                Envelope envelope = envEntry.getKey();
                List<Transaction> envTransactions = envEntry.getValue();
                
                // Calculate average and standard deviation of amounts
                double[] amounts = envTransactions.stream()
                    .mapToDouble(tx -> tx.getAmount().doubleValue())
                    .toArray();
                    
                DoubleSummaryStatistics stats = Arrays.stream(amounts)
                    .summaryStatistics();
                double mean = stats.getAverage();
                double stdDev = calculateStdDev(amounts, mean);
                
                // Look for amounts > 2 standard deviations from mean
                envTransactions.stream()
                    .filter(tx -> tx.getDate().isAfter(LocalDateTime.now().minusWeeks(2)))
                    .forEach(tx -> {
                        double zscore = (tx.getAmount().doubleValue() - mean) / stdDev;
                        if (zscore > 2.0) {
                            insights.add(new SpendingInsight(
                                SpendingInsightType.UNUSUAL_SPENDING,
                                String.format(
                                    "Unusual spending detected in %s using %s: $%.2f " +
                                    "(%.1fx higher than average)",
                                    envelope.getName(),
                                    account.getName(),
                                    tx.getAmount(),
                                    tx.getAmount().doubleValue() / mean
                                ),
                                0.8
                            ));
                        }
                    });
            }
        }

        return insights;
    }

    private List<SpendingInsight> predictUpcomingExpenses() {
        List<SpendingInsight> insights = new ArrayList<>();
        
        // Get historical transactions
        LocalDateTime sixMonthsAgo = LocalDateTime.now().minusMonths(6);
        List<Transaction> transactions = transactionRepository
            .findByDateAfterOrderByDateDesc(sixMonthsAgo);

        // Group by account and envelope
        Map<Account, Map<Envelope, List<Transaction>>> groupedTransactions = transactions.stream()
            .collect(Collectors.groupingBy(
                Transaction::getAccount,
                Collectors.groupingBy(Transaction::getEnvelope)
            ));

        // Analyze each account's envelopes
        for (Map.Entry<Account, Map<Envelope, List<Transaction>>> accountEntry : 
             groupedTransactions.entrySet()) {
            
            Account account = accountEntry.getKey();
            
            for (Map.Entry<Envelope, List<Transaction>> envEntry : 
                 accountEntry.getValue().entrySet()) {
                
                Envelope envelope = envEntry.getKey();
                List<Transaction> envTransactions = envEntry.getValue();
                
                if (envTransactions.size() >= 3) {
                    // Calculate monthly totals
                    Map<YearMonth, BigDecimal> monthlyTotals = envTransactions.stream()
                        .collect(Collectors.groupingBy(
                            tx -> YearMonth.from(tx.getDate()),
                            Collectors.reducing(
                                BigDecimal.ZERO,
                                Transaction::getAmount,
                                BigDecimal::add
                            )
                        ));

                    // Calculate trend
                    double[] totals = monthlyTotals.values().stream()
                        .mapToDouble(BigDecimal::doubleValue)
                        .toArray();
                        
                    double trend = calculateTrend(totals);
                    double currentAvg = Arrays.stream(totals).average().orElse(0.0);
                    double predictedNext = currentAvg * (1 + trend);

                    if (Math.abs(trend) > 0.1) {
                        insights.add(new SpendingInsight(
                            SpendingInsightType.PREDICTED_EXPENSE,
                            String.format(
                                "Predicted %s spending next month using %s: $%.2f " +
                                "(%.1f%% %s than average)",
                                envelope.getName(),
                                account.getName(),
                                predictedNext,
                                Math.abs(trend * 100),
                                trend > 0 ? "higher" : "lower"
                            ),
                            0.7
                        ));
                    }
                }
            }
        }

        return insights;
    }

    private List<SpendingInsight> generateBudgetSuggestions() {
        List<SpendingInsight> insights = new ArrayList<>();
        
        // Get last 6 months of transactions
        LocalDateTime sixMonthsAgo = LocalDateTime.now().minusMonths(6);
        List<Transaction> transactions = transactionRepository
            .findByDateAfterOrderByDateDesc(sixMonthsAgo);

        // Group by account and envelope
        Map<Account, Map<Envelope, List<Transaction>>> groupedTransactions = transactions.stream()
            .collect(Collectors.groupingBy(
                Transaction::getAccount,
                Collectors.groupingBy(Transaction::getEnvelope)
            ));

        // Analyze each account's envelopes
        for (Map.Entry<Account, Map<Envelope, List<Transaction>>> accountEntry : 
             groupedTransactions.entrySet()) {
            
            Account account = accountEntry.getKey();
            
            for (Map.Entry<Envelope, List<Transaction>> envEntry : 
                 accountEntry.getValue().entrySet()) {
                
                Envelope envelope = envEntry.getKey();
                List<Transaction> envTransactions = envEntry.getValue();
                
                // Calculate monthly statistics
                Map<YearMonth, BigDecimal> monthlyTotals = envTransactions.stream()
                    .collect(Collectors.groupingBy(
                        tx -> YearMonth.from(tx.getDate()),
                        Collectors.reducing(
                            BigDecimal.ZERO,
                            Transaction::getAmount,
                            BigDecimal::add
                        )
                    ));

                // Calculate average monthly spending
                double avgMonthlySpend = monthlyTotals.values().stream()
                    .mapToDouble(BigDecimal::doubleValue)
                    .average()
                    .orElse(0.0);

                // Compare with current allocation
                double currentAllocation = envelope.getAllocated().doubleValue();
                double spendRatio = avgMonthlySpend / currentAllocation;

                if (spendRatio > 0.95) {
                    insights.add(new SpendingInsight(
                        SpendingInsightType.BUDGET_SUGGESTION,
                        String.format(
                            "Consider increasing %s budget for %s by %.0f%%. " +
                            "Current allocation of $%.2f is frequently depleted.",
                            envelope.getName(),
                            account.getName(),
                            Math.min((spendRatio - 1) * 100 + 10, 30),
                            currentAllocation
                        ),
                        0.8
                    ));
                } else if (spendRatio < 0.7 && monthlyTotals.size() >= 3) {
                    insights.add(new SpendingInsight(
                        SpendingInsightType.BUDGET_SUGGESTION,
                        String.format(
                            "Consider decreasing %s budget for %s by %.0f%%. " +
                            "Average monthly spending ($%.2f) is well below " +
                            "allocation ($%.2f).",
                            envelope.getName(),
                            account.getName(),
                            Math.min((1 - spendRatio) * 100, 20),
                            avgMonthlySpend,
                            currentAllocation
                        ),
                        0.7
                    ));
                }

                // Check for seasonal patterns
                Map<Month, List<BigDecimal>> monthlySpending = monthlyTotals.entrySet()
                    .stream()
                    .collect(Collectors.groupingBy(
                        e -> e.getKey().getMonth(),
                        Collectors.mapping(
                            Map.Entry::getValue,
                            Collectors.toList()
                        )
                    ));

                monthlySpending.forEach((month, amounts) -> {
                    if (amounts.size() >= 2) {
                        double monthAvg = amounts.stream()
                            .mapToDouble(BigDecimal::doubleValue)
                            .average()
                            .getAsDouble();
                            
                        if (monthAvg > avgMonthlySpend * 1.3) {
                            insights.add(new SpendingInsight(
                                SpendingInsightType.SEASONAL_PATTERN,
                                String.format(
                                    "%s typically needs %.0f%% more budget in %s " +
                                    "for %s. Consider temporary allocation increase.",
                                    envelope.getName(),
                                    ((monthAvg / avgMonthlySpend) - 1) * 100,
                                    month.toString(),
                                    account.getName()
                                ),
                                0.75
                            ));
                        }
                    }
                });
            }
        }

        return insights;
    }

    private List<SpendingInsight> generateAccountSpecificInsights() {
        List<SpendingInsight> insights = new ArrayList<>();
        
        // Get all accounts
        List<Account> accounts = accountRepository.findAll();
        
        for (Account account : accounts) {
            // Get account transactions
            List<Transaction> accountTransactions = transactionRepository
                .findByDateAfterOrderByDateDesc(LocalDateTime.now().minusMonths(3))
                .stream()
                .filter(tx -> tx.getAccount().getId().equals(account.getId()))
                .collect(Collectors.toList());
                
            if (accountTransactions.isEmpty()) continue;
            
            // Add account type specific insights
            if (account.getType() == Account.AccountType.CREDIT_CARD) {
                // Check credit utilization
                BigDecimal balance = account.getBalance();
                // Assuming a standard credit limit, in practice this would come from the account
                BigDecimal estimatedLimit = BigDecimal.valueOf(5000.00);
                double utilizationRate = balance.doubleValue() / estimatedLimit.doubleValue();
                
                if (utilizationRate > 0.7) {
                    insights.add(new SpendingInsight(
                        SpendingInsightType.UNUSUAL_SPENDING,
                        String.format(
                            "High credit utilization (%.0f%%) detected on %s. Consider reducing usage or requesting a limit increase.",
                            utilizationRate * 100,
                            account.getName()
                        ),
                        0.9
                    ));
                }
            } else if (account.getType() == Account.AccountType.CHECKING) {
                // Check for low balance trends
                BigDecimal balance = account.getBalance();
                BigDecimal avgMonthlySpend = accountTransactions.stream()
                    .map(Transaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(3), 2, java.math.RoundingMode.HALF_UP);
                
                if (balance.compareTo(avgMonthlySpend) < 0) {
                    insights.add(new SpendingInsight(
                        SpendingInsightType.BUDGET_SUGGESTION,
                        String.format(
                            "Current balance ($%.2f) in %s is below average monthly spending ($%.2f). Consider maintaining higher balance.",
                            balance,
                            account.getName(),
                            avgMonthlySpend
                        ),
                        0.85
                    ));
                }
            }

            // Analyze transaction patterns
            Map<String, List<Transaction>> merchantPatterns = accountTransactions.stream()
                .collect(Collectors.groupingBy(Transaction::getDescription));
            
            // Look for frequent merchants and spending patterns
            merchantPatterns.forEach((merchant, transactions) -> {
                if (transactions.size() >= 3) {
                    OptionalDouble avgDays = calculateAverageDaysBetween(transactions);
                    if (avgDays.isPresent() && avgDays.getAsDouble() < 35) {
                        double avgAmount = transactions.stream()
                            .mapToDouble(tx -> tx.getAmount().doubleValue())
                            .average()
                            .orElse(0.0);
                            
                        insights.add(new SpendingInsight(
                            SpendingInsightType.RECURRING_PAYMENT,
                            String.format(
                                "Regular payments of $%.2f to %s detected on %s (every %.1f days)",
                                avgAmount,
                                merchant,
                                account.getName(),
                                avgDays.getAsDouble()
                            ),
                            0.85
                        ));
                    }
                }
            });
        }
        
        return insights;
    }

    private List<SpendingInsight> detectCrossAccountPatterns() {
        List<SpendingInsight> insights = new ArrayList<>();
        
        // Get recent transactions across all accounts
        LocalDateTime threeMonthsAgo = LocalDateTime.now().minusMonths(3);
        List<Transaction> recentTransactions = transactionRepository
            .findByDateAfterOrderByDateDesc(threeMonthsAgo);
            
        // Group by merchant across accounts
        Map<String, Map<Account, List<Transaction>>> merchantAccountPatterns = 
            recentTransactions.stream()
                .collect(Collectors.groupingBy(
                    Transaction::getDescription,
                    Collectors.groupingBy(Transaction::getAccount)
                ));
                
        // Look for merchants used across multiple accounts
        merchantAccountPatterns.forEach((merchant, accountTransactions) -> {
            if (accountTransactions.size() > 1) {
                // Calculate total spent per account
                Map<Account, BigDecimal> accountTotals = new HashMap<>();
                accountTransactions.forEach((account, transactions) -> {
                    BigDecimal total = transactions.stream()
                        .map(Transaction::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                    accountTotals.put(account, total);
                });
                
                // If significant spending across accounts, suggest consolidation
                if (accountTotals.size() > 1) {
                    String accountsList = accountTotals.entrySet().stream()
                        .map(e -> String.format("%s ($%.2f)", 
                            e.getKey().getName(), 
                            e.getValue()))
                        .collect(Collectors.joining(", "));
                        
                    insights.add(new SpendingInsight(
                        SpendingInsightType.REALLOCATION_SUGGESTION,
                        String.format(
                            "Multiple accounts used for %s: %s. Consider consolidating to one account for better tracking.",
                            merchant,
                            accountsList
                        ),
                        0.7
                    ));
                }
            }
        });
        
        return insights;
    }

    // Helper methods
    private OptionalDouble calculateAverageDaysBetween(List<Transaction> transactions) {
        if (transactions.size() < 2) {
            return OptionalDouble.empty();
        }

        List<Transaction> sorted = transactions.stream()
            .sorted(Comparator.comparing(Transaction::getDate))
            .collect(Collectors.toList());

        double totalDays = 0;
        int count = 0;

        for (int i = 1; i < sorted.size(); i++) {
            Duration duration = Duration.between(
                sorted.get(i-1).getDate(),
                sorted.get(i).getDate()
            );
            totalDays += duration.toDays();
            count++;
        }

        return count > 0 ? 
            OptionalDouble.of(totalDays / count) : 
            OptionalDouble.empty();
    }

    private double calculateStdDev(double[] values, double mean) {
        return Math.sqrt(
            Arrays.stream(values)
                .map(v -> Math.pow(v - mean, 2))
                .average()
                .orElse(0.0)
        );
    }

    private double calculateStdDev(List<Transaction> transactions, double avgDays) {
        if (transactions.size() < 2) {
            return Double.MAX_VALUE;
        }

        List<Transaction> sorted = transactions.stream()
            .sorted(Comparator.comparing(Transaction::getDate))
            .collect(Collectors.toList());

        double sumSquaredDiff = 0;
        int count = 0;

        for (int i = 1; i < sorted.size(); i++) {
            double days = Duration.between(
                sorted.get(i-1).getDate(),
                sorted.get(i).getDate()
            ).toDays();
            sumSquaredDiff += Math.pow(days - avgDays, 2);
            count++;
        }

        return count > 0 ? 
            Math.sqrt(sumSquaredDiff / count) : 
            Double.MAX_VALUE;
    }

    private double calculateTrend(double[] values) {
        if (values.length < 2) {
            return 0.0;
        }

        // Simple linear regression to calculate trend
        int n = values.length;
        double sumX = 0;
        double sumY = 0;
        double sumXY = 0;
        double sumXX = 0;

        for (int i = 0; i < n; i++) {
            sumX += i;
            sumY += values[i];
            sumXY += i * values[i];
            sumXX += i * i;
        }

        double slope = (n * sumXY - sumX * sumY) / (n * sumXX - sumX * sumX);
        double meanY = sumY / n;

        // Return normalized trend (as percentage change)
        return slope / meanY;
    }
}