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
package org.emblow.envelopify.service.ml;

/**
 *
 * @author Nicholas J Emblow
 */

import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.emblow.envelopify.domain.Transaction;
import org.emblow.envelopify.domain.Envelope;
import org.emblow.envelopify.domain.Account;
import org.emblow.envelopify.repositry.TransactionRepository;
import org.emblow.envelopify.repositry.EnvelopeRepository;
import org.emblow.envelopify.repositry.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.math.BigDecimal;
import java.util.stream.Collectors;

@Service
public class AdvancedMLService {
    private static final Logger log = LoggerFactory.getLogger(AdvancedMLService.class);
    
    private final TransactionRepository transactionRepository;
    private final EnvelopeRepository envelopeRepository;
    private final AccountRepository accountRepository;
    private final TransactionClassifier classifier;
    
    public AdvancedMLService(
        TransactionRepository transactionRepository,
        EnvelopeRepository envelopeRepository,
        AccountRepository accountRepository,
        TransactionClassifier classifier
    ) {
        this.transactionRepository = transactionRepository;
        this.envelopeRepository = envelopeRepository;
        this.accountRepository = accountRepository;
        this.classifier = classifier;
    }
    
    @Scheduled(cron = "0 0 2 * * *") // Run at 2 AM daily
    public void retrainModels() {
        log.info("Starting daily model retraining");
        classifier.retrain();
    }
    
    public List<AccountAnalysis> analyzeAccounts() {
        List<AccountAnalysis> analyses = new ArrayList<>();
        
        try {
            // Get all accounts
            List<Account> accounts = accountRepository.findAll();
            
            for (Account account : accounts) {
                analyses.add(analyzeAccount(account));
            }
            
            // Add cross-account analysis if we have multiple accounts
            if (accounts.size() > 1) {
                addCrossAccountAnalysis(analyses);
            }
            
        } catch (Exception e) {
            log.error("Error analyzing accounts", e);
        }
        
        return analyses;
    }
    
    private AccountAnalysis analyzeAccount(Account account) {
        // Get recent transactions
        LocalDateTime sixMonthsAgo = LocalDateTime.now().minusMonths(6);
        List<Transaction> accountTransactions = transactionRepository
            .findByDateAfterOrderByDateDesc(sixMonthsAgo)
            .stream()
            .filter(tx -> tx.getAccount().getId().equals(account.getId()))
            .collect(Collectors.toList());
            
        // Calculate key metrics
        Map<YearMonth, BigDecimal> monthlyVolumes = accountTransactions.stream()
            .collect(Collectors.groupingBy(
                tx -> YearMonth.from(tx.getDate()),
                Collectors.reducing(
                    BigDecimal.ZERO,
                    Transaction::getAmount,
                    BigDecimal::add
                )
            ));
            
        // Monthly statistics
        DoubleSummaryStatistics volumeStats = monthlyVolumes.values().stream()
            .mapToDouble(BigDecimal::doubleValue)
            .summaryStatistics();
            
        // Merchant analysis
        Map<String, MerchantMetrics> merchantMetrics = analyzeMerchants(accountTransactions);
        
        // Envelope usage patterns
        Map<Envelope, EnvelopeMetrics> envelopeMetrics = analyzeEnvelopes(accountTransactions);
        
        // Anomaly detection
        List<AnomalyDetection> anomalies = detectAnomaliesForAccount(accountTransactions);
        
        // Weekly patterns
        Map<DayOfWeek, Double> dayOfWeekPatterns = analyzeDayOfWeekPatterns(accountTransactions);
        
        // Balance trends
        BalanceTrends balanceTrends = analyzeBalanceTrends(account, accountTransactions);
        
        return new AccountAnalysis(
            account,
            volumeStats.getAverage(),
            calculateTrend(new ArrayList<>(monthlyVolumes.values())),
            new ArrayList<>(merchantMetrics.entrySet().stream()
                .sorted((e1, e2) -> Double.compare(e2.getValue().totalSpent, e1.getValue().totalSpent))
                .limit(5)
                .map(Map.Entry::getKey)
                .toList()),
            anomalies,
            merchantMetrics,
            envelopeMetrics,
            dayOfWeekPatterns,
            balanceTrends
        );
    }
    
    private Map<String, MerchantMetrics> analyzeMerchants(List<Transaction> transactions) {
        Map<String, MerchantMetrics> metrics = new HashMap<>();
        
        // Group transactions by merchant
        Map<String, List<Transaction>> byMerchant = transactions.stream()
            .collect(Collectors.groupingBy(Transaction::getDescription));
            
        byMerchant.forEach((merchant, merchantTxs) -> {
            double totalSpent = merchantTxs.stream()
                .mapToDouble(tx -> tx.getAmount().doubleValue())
                .sum();
                
            double avgAmount = merchantTxs.stream()
                .mapToDouble(tx -> tx.getAmount().doubleValue())
                .average()
                .orElse(0.0);
                
            OptionalDouble avgDaysBetween = calculateAverageDaysBetween(merchantTxs);
            
            double frequency = (double) merchantTxs.size() / 
                ChronoUnit.MONTHS.between(
                    merchantTxs.get(merchantTxs.size() - 1).getDate(),
                    merchantTxs.get(0).getDate()
                );
                
            Map<Envelope, Integer> envelopeUsage = merchantTxs.stream()
                .collect(Collectors.groupingBy(
                    Transaction::getEnvelope,
                    Collectors.collectingAndThen(Collectors.counting(), Long::intValue)
                ));
                
            metrics.put(merchant, new MerchantMetrics(
                totalSpent,
                avgAmount,
                frequency,
                avgDaysBetween.orElse(0.0),
                envelopeUsage
            ));
        });
        
        return metrics;
    }
    
    private Map<Envelope, EnvelopeMetrics> analyzeEnvelopes(List<Transaction> transactions) {
        Map<Envelope, EnvelopeMetrics> metrics = new HashMap<>();
        
        // Group transactions by envelope
        Map<Envelope, List<Transaction>> byEnvelope = transactions.stream()
            .collect(Collectors.groupingBy(Transaction::getEnvelope));
            
        byEnvelope.forEach((envelope, envTxs) -> {
            double totalSpent = envTxs.stream()
                .mapToDouble(tx -> tx.getAmount().doubleValue())
                .sum();
                
            Map<YearMonth, Double> monthlySpending = envTxs.stream()
                .collect(Collectors.groupingBy(
                    tx -> YearMonth.from(tx.getDate()),
                    Collectors.summingDouble(tx -> tx.getAmount().doubleValue())
                ));
                
            double trend = calculateTrend(monthlySpending.values().stream()
                .mapToDouble(Double::doubleValue)
                .toArray());
                
            metrics.put(envelope, new EnvelopeMetrics(
                totalSpent,
                monthlySpending,
                trend,
                envelope.getBudgetUtilization()
            ));
        });
        
        return metrics;
    }
    
    private Map<DayOfWeek, Double> analyzeDayOfWeekPatterns(List<Transaction> transactions) {
        return transactions.stream()
            .collect(Collectors.groupingBy(
                tx -> tx.getDate().getDayOfWeek(),
                Collectors.averagingDouble(tx -> tx.getAmount().doubleValue())
            ));
    }
    
    private BalanceTrends analyzeBalanceTrends(Account account, List<Transaction> transactions) {
        if (transactions.isEmpty()) {
            return new BalanceTrends(0.0, 0.0, 0.0, Collections.emptyMap());
        }
        
        // Calculate daily balances
        TreeMap<LocalDate, Double> dailyBalances = new TreeMap<>();
        double runningBalance = account.getBalance().doubleValue();
        
        // Work backwards from current balance
        for (Transaction tx : transactions) {
            LocalDate date = tx.getDate().toLocalDate();
            runningBalance -= tx.getAmount().doubleValue();
            dailyBalances.merge(date, runningBalance, Double::sum);
        }
        
        // Calculate metrics
        DoubleSummaryStatistics stats = dailyBalances.values().stream()
            .mapToDouble(Double::doubleValue)
            .summaryStatistics();
            
        double volatility = Math.sqrt(dailyBalances.values().stream()
            .mapToDouble(balance -> Math.pow(balance - stats.getAverage(), 2))
            .average()
            .orElse(0.0));
            
        return new BalanceTrends(
            stats.getAverage(),
            volatility,
            calculateTrend(dailyBalances.values().stream()
                .mapToDouble(Double::doubleValue)
                .toArray()),
            dailyBalances
        );
    }
    
    private void addCrossAccountAnalysis(List<AccountAnalysis> analyses) {
        // Find common merchants across accounts
        Map<String, Set<Account>> merchantAccounts = new HashMap<>();
        
        for (AccountAnalysis analysis : analyses) {
            for (String merchant : analysis.getMerchantMetrics().keySet()) {
                merchantAccounts.computeIfAbsent(merchant, k -> new HashSet<>())
                    .add(analysis.getAccount());
            }
        }
        
        // Add cross-account insights to each analysis
        for (AccountAnalysis analysis : analyses) {
            List<String> sharedMerchants = merchantAccounts.entrySet().stream()
                .filter(e -> e.getValue().size() > 1 && 
                            e.getValue().contains(analysis.getAccount()))
                .map(Map.Entry::getKey)
                .toList();
                
            analysis.setCrossAccountMetrics(new CrossAccountMetrics(
                sharedMerchants,
                analyses.stream()
                    .filter(a -> !a.equals(analysis))
                    .collect(Collectors.toMap(
                        a -> a.getAccount(),
                        a -> calculateAccountSimilarity(analysis, a)
                    ))
            ));
        }
    }
    
    private double calculateAccountSimilarity(AccountAnalysis a1, AccountAnalysis a2) {
        Set<String> merchants1 = a1.getMerchantMetrics().keySet();
        Set<String> merchants2 = a2.getMerchantMetrics().keySet();
        
        // Jaccard similarity of merchant sets
        Set<String> intersection = new HashSet<>(merchants1);
        intersection.retainAll(merchants2);
        
        Set<String> union = new HashSet<>(merchants1);
        union.addAll(merchants2);
        
        return union.isEmpty() ? 0.0 : 
            (double) intersection.size() / union.size();
    }
    
    public List<AnomalyDetection> detectAnomaliesForAccount(List<Transaction> transactions) {
        List<AnomalyDetection> anomalies = new ArrayList<>();
        
        if (transactions.isEmpty()) return anomalies;
        
        // Calculate statistical measures
        DoubleSummaryStatistics stats = transactions.stream()
            .mapToDouble(tx -> tx.getAmount().doubleValue())
            .summaryStatistics();
            
        double mean = stats.getAverage();
        double stdDev = calculateStdDev(transactions, mean);
        
        // Check recent transactions for anomalies
        transactions.stream()
            .filter(tx -> tx.getDate().isAfter(LocalDateTime.now().minusWeeks(2)))
            .forEach(tx -> {
                double zscore = (tx.getAmount().doubleValue() - mean) / stdDev;
                if (Math.abs(zscore) > 2.5) {
                    anomalies.add(new AnomalyDetection(
                        tx,
                        AnomalyType.AMOUNT,
                        Math.abs(zscore),
                        String.format(
                            "Unusual transaction amount for %s: $%.2f",
                            tx.getAccount().getName(),
                            tx.getAmount()
                        )
                    ));
                }
            });
            
        // Check for unusual frequency
        Map<LocalDate, Long> dailyCounts = transactions.stream()
            .collect(Collectors.groupingBy(
                tx -> tx.getDate().toLocalDate(),
                Collectors.counting()
            ));
            
        DoubleSummaryStatistics freqStats = dailyCounts.values().stream()
            .mapToDouble(Long::doubleValue)
            .summaryStatistics();
            
        double freqMean = freqStats.getAverage();
        double freqStdDev = calculateStdDev(
            dailyCounts.values().stream()
                .mapToDouble(Long::doubleValue)
                .toArray(),
            freqMean
        );
        
        dailyCounts.forEach((date, count) -> {
            if (date.isAfter(LocalDate.now().minusWeeks(2))) {
                double zscore = (count - freqMean) / freqStdDev;
                if (zscore > 2.5) {
                    anomalies.add(new AnomalyDetection(
                        null,
                        AnomalyType.FREQUENCY,
                        zscore,
                        String.format(
                            "Unusual number of transactions (%d) on %s",
                            count,
                            date
                        )
                    ));
                }
            }
        });
        
        return anomalies;
    }
    
    // Helper classes for analysis results
    public record MerchantMetrics(
        double totalSpent,
        double averageAmount,
        double monthlyFrequency,
        double averageDaysBetween,
        Map<Envelope, Integer> envelopeUsage
    ) {}
    
    public record EnvelopeMetrics(
        double totalSpent,
        Map<YearMonth, Double> monthlySpending,
        double spendingTrend,
        double budgetUtilization
    ) {}
    
    public record BalanceTrends(
        double averageBalance,
        double balanceVolatility,
        double balanceTrend,
        Map<LocalDate, Double> dailyBalances
    ) {}
    
    public record CrossAccountMetrics(
        List<String> sharedMerchants,
        Map<Account, Double> accountSimilarities
    ) {}
    
    // Main analysis result class
    public static class AccountAnalysis {
        private final Account account;
        private final double averageMonthlyVolume;
        private final double volumeTrend;
        private final List<String> topMerchants;
        private final List<AnomalyDetection> anomalies;
        private final Map<String, MerchantMetrics> merchantMetrics;
        private final Map<Envelope, EnvelopeMetrics> envelopeMetrics;
        private final Map<DayOfWeek, Double> dayOfWeekPatterns;
        private final BalanceTrends balanceTrends;
        private CrossAccountMetrics crossAccountMetrics;

        public AccountAnalysis(
            Account account,
            double averageMonthlyVolume,
            double volumeTrend,
            List<String> topMerchants,
            List<AnomalyDetection> anomalies,
            Map<String, MerchantMetrics> merchantMetrics,
            Map<Envelope, EnvelopeMetrics> envelopeMetrics,
            Map<DayOfWeek, Double> dayOfWeekPatterns,
            BalanceTrends balanceTrends
        ) {
            this.account = account;
            this.averageMonthlyVolume = averageMonthlyVolume;
            this.volumeTrend = volumeTrend;
            this.topMerchants = topMerchants;
            this.anomalies = anomalies;
            this.merchantMetrics = merchantMetrics;
            this.envelopeMetrics = envelopeMetrics;
            this.dayOfWeekPatterns = dayOfWeekPatterns;
            this.balanceTrends = balanceTrends;
            this.crossAccountMetrics = null;
        }

        public void setCrossAccountMetrics(CrossAccountMetrics metrics) {
            this.crossAccountMetrics = metrics;
        }

        // Getters
        public Account getAccount() { return account; }
        public double getAverageMonthlyVolume() { return averageMonthlyVolume; }
        public double getVolumeTrend() { return volumeTrend; }
        public List<String> getTopMerchants() { return topMerchants; }
        public List<AnomalyDetection> getAnomalies() { return anomalies; }
        public Map<String, MerchantMetrics> getMerchantMetrics() { return merchantMetrics; }
        public Map<Envelope, EnvelopeMetrics> getEnvelopeMetrics() { return envelopeMetrics; }
        public Map<DayOfWeek, Double> getDayOfWeekPatterns() { return dayOfWeekPatterns; }
        public BalanceTrends getBalanceTrends() { return balanceTrends; }
        public CrossAccountMetrics getCrossAccountMetrics() { return crossAccountMetrics; }
    }

    public enum AnomalyType {
        AMOUNT,
        FREQUENCY,
        PATTERN
    }

    public record AnomalyDetection(
        Transaction transaction,
        AnomalyType type,
        double severity,
        String description
    ) {}

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

    private double calculateStdDev(List<Transaction> transactions, double mean) {
        return Math.sqrt(
            transactions.stream()
                .mapToDouble(tx -> Math.pow(tx.getAmount().doubleValue() - mean, 2))
                .average()
                .orElse(0.0)
        );
    }

    private double calculateStdDev(double[] values, double mean) {
        return Math.sqrt(
            Arrays.stream(values)
                .map(v -> Math.pow(v - mean, 2))
                .average()
                .orElse(0.0)
        );
    }

    private double calculateTrend(double[] values) {
        if (values.length < 2) {
            return 0.0;
        }

        // Simple linear regression
        int n = values.length;
        double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;

        for (int i = 0; i < n; i++) {
            sumX += i;
            sumY += values[i];
            sumXY += i * values[i];
            sumXX += i * i;
        }

        double slope = (n * sumXY - sumX * sumY) / (n * sumXX - sumX * sumX);
        double meanY = sumY / n;

        return slope / meanY; // Return normalized trend
    }

    private double calculateTrend(List<BigDecimal> values) {
        return calculateTrend(values.stream()
            .mapToDouble(BigDecimal::doubleValue)
            .toArray());
    }
}