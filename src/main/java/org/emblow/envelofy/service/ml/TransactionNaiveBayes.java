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
import java.util.*;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.Month;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.emblow.envelofy.domain.Account;
import java.util.stream.Collectors;

public class TransactionNaiveBayes {
    private static final Logger log = LoggerFactory.getLogger(TransactionNaiveBayes.class);
    
    // Probability tables
    private final Map<String, Double> categoryPriors = new HashMap<>();
    private final Map<String, Map<String, Double>> wordLikelihoods = new HashMap<>();
    private final Map<String, GaussianDistribution> amountDistributions = new HashMap<>();
    private final Map<String, Map<DayOfWeek, Double>> dayOfWeekLikelihoods = new HashMap<>();
    private final Map<String, Map<Month, Double>> monthLikelihoods = new HashMap<>();
    private final Map<String, Map<Account.AccountType, Double>> accountTypeLikelihoods = new HashMap<>();
    private final Map<String, Map<String, Double>> accountNameLikelihoods = new HashMap<>();
    
    // Subscription-specific fields
    private boolean subscriptionMode = false; // Toggle between envelope and subscription prediction
    private double subscriptionPrior = 0.5; // P(subscription)
    private double nonSubscriptionPrior = 0.5; // P(non-subscription)
    private final Map<String, Double> subscriptionWordLikelihoods = new HashMap<>();
    private final Map<String, Double> nonSubscriptionWordLikelihoods = new HashMap<>();
    private GaussianDistribution subscriptionAmountDist = new GaussianDistribution(0, MIN_STD_DEV);
    private GaussianDistribution nonSubscriptionAmountDist = new GaussianDistribution(0, MIN_STD_DEV);
    private final Map<DayOfWeek, Double> subscriptionDayLikelihoods = new HashMap<>();
    private final Map<DayOfWeek, Double> nonSubscriptionDayLikelihoods = new HashMap<>();
    private final Map<Month, Double> subscriptionMonthLikelihoods = new HashMap<>();
    private final Map<Month, Double> nonSubscriptionMonthLikelihoods = new HashMap<>();
    private final Map<Account.AccountType, Double> subscriptionAccountTypeLikelihoods = new HashMap<>();
    private final Map<Account.AccountType, Double> nonSubscriptionAccountTypeLikelihoods = new HashMap<>();
    private final Map<String, Double> subscriptionAccountNameLikelihoods = new HashMap<>();
    private final Map<String, Double> nonSubscriptionAccountNameLikelihoods = new HashMap<>();
    private int subscriptionCount = 0;
    private int nonSubscriptionCount = 0;
    
    // Smoothing parameters
    private static final double ALPHA = 1.0;  // Laplace smoothing
    private static final double MIN_STD_DEV = 0.01;  // Minimum standard deviation
    
    // Feature weights
    private static final double WORD_WEIGHT = 0.4;
    private static final double AMOUNT_WEIGHT = 0.25;
    private static final double TIME_WEIGHT = 0.15;
    private static final double ACCOUNT_TYPE_WEIGHT = 0.1;
    private static final double ACCOUNT_NAME_WEIGHT = 0.1;
    
    private final Set<String> vocabulary = new HashSet<>();
    private final TextPreprocessor textPreprocessor;
    
    public TransactionNaiveBayes() {
        this(false); // Default to envelope mode
    }
    
    public TransactionNaiveBayes(boolean subscriptionMode) {
        this.textPreprocessor = new TextPreprocessor();
        this.subscriptionMode = subscriptionMode;
    }
    
    public void train(List<TransactionFeatures> transactions) {
        log.info("Training NBC with {} transactions in {} mode", transactions.size(), 
            subscriptionMode ? "subscription" : "envelope");
            
        if (subscriptionMode) {
            subscriptionCount = 0;
            nonSubscriptionCount = 0;
            vocabulary.clear();
            
            for (TransactionFeatures tx : transactions) {
                boolean isSubscription = tx.category().equals("SUBSCRIPTION"); // Placeholder label
                trainSubscription(tx, isSubscription);
            }
        } else {
            categoryPriors.clear();
            wordLikelihoods.clear();
            amountDistributions.clear();
            dayOfWeekLikelihoods.clear();
            monthLikelihoods.clear();
            accountTypeLikelihoods.clear();
            accountNameLikelihoods.clear();
            vocabulary.clear();
            
            Map<String, Integer> categoryCounts = new HashMap<>();
            for (TransactionFeatures tx : transactions) {
                categoryCounts.merge(tx.category(), 1, Integer::sum);
            }
            
            int totalTransactions = transactions.size();
            categoryCounts.forEach((category, count) -> 
                categoryPriors.put(category, (count.doubleValue() + ALPHA) / 
                    (totalTransactions + ALPHA * categoryCounts.size())));
            
            transactions.forEach(tx -> 
                vocabulary.addAll(Arrays.asList(textPreprocessor.tokenize(tx.description()))));
            
            for (String category : categoryCounts.keySet()) {
                wordLikelihoods.put(category, new HashMap<>());
                dayOfWeekLikelihoods.put(category, new HashMap<>());
                monthLikelihoods.put(category, new HashMap<>());
                accountTypeLikelihoods.put(category, new HashMap<>());
                accountNameLikelihoods.put(category, new HashMap<>());
            }
            
            calculateWordLikelihoods(transactions, categoryCounts);
            calculateAmountDistributions(transactions, categoryCounts);
            calculateTemporalLikelihoods(transactions, categoryCounts);
            calculateAccountLikelihoods(transactions, categoryCounts);
        }
        
        log.info("Training complete. Vocabulary size: {}", vocabulary.size());
    }
    
    public Map<String, Double> predict(TransactionFeatures transaction) {
        if (subscriptionMode) {
            double probSub = predictSubscriptionProbability(transaction);
            return Map.of("SUBSCRIPTION", probSub, "NON_SUBSCRIPTION", 1.0 - probSub);
        } else {
            Map<String, Double> scores = new HashMap<>();
            double totalScore = 0.0;
            
            for (String category : categoryPriors.keySet()) {
                double score = Math.log(categoryPriors.get(category));
                
                String[] words = textPreprocessor.tokenize(transaction.description());
                double wordScore = 0.0;
                for (String word : words) {
                    if (vocabulary.contains(word)) {
                        wordScore += Math.log(wordLikelihoods.get(category)
                            .getOrDefault(word, ALPHA / (vocabulary.size() * ALPHA)));
                    }
                }
                
                double amountScore = amountDistributions.get(category)
                    .logProbability(transaction.amount().doubleValue());
                
                double dayScore = Math.log(dayOfWeekLikelihoods.get(category)
                    .getOrDefault(transaction.date().getDayOfWeek(), ALPHA / (7 * ALPHA)));
                double monthScore = Math.log(monthLikelihoods.get(category)
                    .getOrDefault(transaction.date().getMonth(), ALPHA / (12 * ALPHA)));
                    
                double accountTypeScore = Math.log(accountTypeLikelihoods.get(category)
                    .getOrDefault(transaction.accountType(), 
                        ALPHA / (Account.AccountType.values().length * ALPHA)));
                double accountNameScore = Math.log(accountNameLikelihoods.get(category)
                    .getOrDefault(transaction.accountName(), ALPHA / accountNameLikelihoods.size()));
                
                score += WORD_WEIGHT * wordScore +
                        AMOUNT_WEIGHT * amountScore +
                        TIME_WEIGHT * (dayScore + monthScore) +
                        ACCOUNT_TYPE_WEIGHT * accountTypeScore +
                        ACCOUNT_NAME_WEIGHT * accountNameScore;
                
                scores.put(category, Math.exp(score));
                totalScore += Math.exp(score);
            }
            
            Map<String, Double> normalizedScores = new HashMap<>();
            for (Map.Entry<String, Double> entry : scores.entrySet()) {
                normalizedScores.put(entry.getKey(), entry.getValue() / totalScore);
            }
            
            return normalizedScores;
        }
    }
    
    private void calculateWordLikelihoods(
        List<TransactionFeatures> transactions,
        Map<String, Integer> categoryCounts
    ) {
        Map<String, Map<String, Integer>> wordCounts = new HashMap<>();
        categoryCounts.keySet().forEach(category -> 
            wordCounts.put(category, new HashMap<>()));
        
        for (TransactionFeatures tx : transactions) {
            String[] words = textPreprocessor.tokenize(tx.description());
            for (String word : words) {
                wordCounts.get(tx.category()).merge(word, 1, Integer::sum);
            }
        }
        
        for (String category : categoryCounts.keySet()) {
            int totalWords = wordCounts.get(category).values().stream()
                .mapToInt(Integer::intValue).sum();
            
            for (String word : vocabulary) {
                int count = wordCounts.get(category).getOrDefault(word, 0);
                double likelihood = (count + ALPHA) / 
                    (totalWords + ALPHA * vocabulary.size());
                wordLikelihoods.get(category).put(word, likelihood);
            }
        }
    }
    
    private void calculateAccountLikelihoods(
        List<TransactionFeatures> transactions,
        Map<String, Integer> categoryCounts
    ) {
        Map<String, Map<Account.AccountType, Integer>> typeCountsByCategory = new HashMap<>();
        Map<String, Map<String, Integer>> nameCountsByCategory = new HashMap<>();
        
        for (TransactionFeatures tx : transactions) {
            typeCountsByCategory
                .computeIfAbsent(tx.category(), k -> new HashMap<>())
                .merge(tx.accountType(), 1, Integer::sum);
                
            nameCountsByCategory
                .computeIfAbsent(tx.category(), k -> new HashMap<>())
                .merge(tx.accountName(), 1, Integer::sum);
        }
        
        for (String category : categoryCounts.keySet()) {
            int totalTx = categoryCounts.get(category);
            
            for (Account.AccountType type : Account.AccountType.values()) {
                int count = typeCountsByCategory
                    .getOrDefault(category, Map.of())
                    .getOrDefault(type, 0);
                    
                double likelihood = (count + ALPHA) / 
                    (totalTx + ALPHA * Account.AccountType.values().length);
                    
                accountTypeLikelihoods.get(category).put(type, likelihood);
            }
            
            Set<String> allAccountNames = nameCountsByCategory.values().stream()
                .flatMap(m -> m.keySet().stream())
                .collect(Collectors.toSet());
                
            for (String name : allAccountNames) {
                int count = nameCountsByCategory
                    .getOrDefault(category, Map.of())
                    .getOrDefault(name, 0);
                    
                double likelihood = (count + ALPHA) / 
                    (totalTx + ALPHA * allAccountNames.size());
                    
                accountNameLikelihoods.get(category).put(name, likelihood);
            }
        }
    }
    
    private void calculateAmountDistributions(
        List<TransactionFeatures> transactions,
        Map<String, Integer> categoryCounts
    ) {
        Map<String, List<Double>> amountsByCategory = new HashMap<>();
        categoryCounts.keySet().forEach(category -> 
            amountsByCategory.put(category, new ArrayList<>()));
        
        transactions.forEach(tx -> 
            amountsByCategory.get(tx.category())
                .add(tx.amount().doubleValue()));
        
        amountsByCategory.forEach((category, amounts) -> {
            double mean = amounts.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
            
            double variance = amounts.stream()
                .mapToDouble(a -> Math.pow(a - mean, 2))
                .average()
                .orElse(MIN_STD_DEV);
            
            amountDistributions.put(category, 
                new GaussianDistribution(mean, Math.sqrt(variance)));
        });
    }
    
    private void calculateTemporalLikelihoods(
        List<TransactionFeatures> transactions,
        Map<String, Integer> categoryCounts
    ) {
        Map<String, Map<DayOfWeek, Integer>> dayCountsByCategory = new HashMap<>();
        Map<String, Map<Month, Integer>> monthCountsByCategory = new HashMap<>();
        
        categoryCounts.keySet().forEach(category -> {
            dayCountsByCategory.put(category, new HashMap<>());
            monthCountsByCategory.put(category, new HashMap<>());
        });
        
        for (TransactionFeatures tx : transactions) {
            dayCountsByCategory.get(tx.category())
                .merge(tx.date().getDayOfWeek(), 1, Integer::sum);
            monthCountsByCategory.get(tx.category())
                .merge(tx.date().getMonth(), 1, Integer::sum);
        }
        
        for (String category : categoryCounts.keySet()) {
            int totalDays = dayCountsByCategory.get(category).values().stream()
                .mapToInt(Integer::intValue).sum();
            
            for (DayOfWeek day : DayOfWeek.values()) {
                int count = dayCountsByCategory.get(category)
                    .getOrDefault(day, 0);
                double likelihood = (count + ALPHA) / (totalDays + 7 * ALPHA);
                dayOfWeekLikelihoods.get(category).put(day, likelihood);
            }
            
            int totalMonths = monthCountsByCategory.get(category).values().stream()
                .mapToInt(Integer::intValue).sum();
            
            for (Month month : Month.values()) {
                int count = monthCountsByCategory.get(category)
                    .getOrDefault(month, 0);
                double likelihood = (count + ALPHA) / (totalMonths + 12 * ALPHA);
                monthLikelihoods.get(category).put(month, likelihood);
            }
        }
    }
    
    public synchronized void trainSubscription(TransactionFeatures transaction, boolean isSubscription) {
        subscriptionMode = true;
        List<TransactionFeatures> singleTx = Collections.singletonList(transaction);
        
        if (subscriptionCount == 0 && nonSubscriptionCount == 0) {
            vocabulary.addAll(Arrays.asList(textPreprocessor.tokenize(transaction.description())));
        }
        
        if (isSubscription) {
            subscriptionCount++;
        } else {
            nonSubscriptionCount++;
        }
        
        int total = subscriptionCount + nonSubscriptionCount;
        subscriptionPrior = (subscriptionCount + ALPHA) / (total + 2 * ALPHA);
        nonSubscriptionPrior = (nonSubscriptionCount + ALPHA) / (total + 2 * ALPHA);
        
        updateWordLikelihoods(singleTx, isSubscription);
        updateAmountDistributions(singleTx, isSubscription);
        updateTemporalLikelihoods(singleTx, isSubscription);
        updateAccountLikelihoods(singleTx, isSubscription);
    }

    public double predictSubscriptionProbability(TransactionFeatures transaction) {
        subscriptionMode = true;
        double logProbSub = Math.log(subscriptionPrior);
        double logProbNonSub = Math.log(nonSubscriptionPrior);
        
        String[] words = textPreprocessor.tokenize(transaction.description());
        for (String word : words) {
            if (vocabulary.contains(word)) {
                logProbSub += Math.log(subscriptionWordLikelihoods.getOrDefault(word, 
                    ALPHA / (vocabulary.size() * ALPHA)));
                logProbNonSub += Math.log(nonSubscriptionWordLikelihoods.getOrDefault(word, 
                    ALPHA / (vocabulary.size() * ALPHA)));
            }
        }
        
        logProbSub += AMOUNT_WEIGHT * subscriptionAmountDist.logProbability(transaction.amount().doubleValue());
        logProbNonSub += AMOUNT_WEIGHT * nonSubscriptionAmountDist.logProbability(transaction.amount().doubleValue());
        
        double dayScoreSub = Math.log(subscriptionDayLikelihoods.getOrDefault(transaction.date().getDayOfWeek(), 
            ALPHA / (7 * ALPHA)));
        double dayScoreNonSub = Math.log(nonSubscriptionDayLikelihoods.getOrDefault(transaction.date().getDayOfWeek(), 
            ALPHA / (7 * ALPHA)));
        double monthScoreSub = Math.log(subscriptionMonthLikelihoods.getOrDefault(transaction.date().getMonth(), 
            ALPHA / (12 * ALPHA)));
        double monthScoreNonSub = Math.log(nonSubscriptionMonthLikelihoods.getOrDefault(transaction.date().getMonth(), 
            ALPHA / (12 * ALPHA)));
        
        logProbSub += TIME_WEIGHT * (dayScoreSub + monthScoreSub);
        logProbNonSub += TIME_WEIGHT * (dayScoreNonSub + monthScoreNonSub);
        
        double typeScoreSub = Math.log(subscriptionAccountTypeLikelihoods.getOrDefault(transaction.accountType(), 
            ALPHA / (Account.AccountType.values().length * ALPHA)));
        double typeScoreNonSub = Math.log(nonSubscriptionAccountTypeLikelihoods.getOrDefault(transaction.accountType(), 
            ALPHA / (Account.AccountType.values().length * ALPHA)));
        double nameScoreSub = Math.log(subscriptionAccountNameLikelihoods.getOrDefault(transaction.accountName(), 
            ALPHA / accountNameLikelihoods.size()));
        double nameScoreNonSub = Math.log(nonSubscriptionAccountNameLikelihoods.getOrDefault(transaction.accountName(), 
            ALPHA / accountNameLikelihoods.size()));
        
        logProbSub += ACCOUNT_TYPE_WEIGHT * typeScoreSub + ACCOUNT_NAME_WEIGHT * nameScoreSub;
        logProbNonSub += ACCOUNT_TYPE_WEIGHT * typeScoreNonSub + ACCOUNT_NAME_WEIGHT * nameScoreNonSub;
        
        double probSub = Math.exp(logProbSub) / (Math.exp(logProbSub) + Math.exp(logProbNonSub));
        return Double.isNaN(probSub) ? 0.5 : probSub;
    }

    private void updateWordLikelihoods(List<TransactionFeatures> transactions, boolean isSubscription) {
        Map<String, Integer> wordCounts = isSubscription ? 
            subscriptionWordLikelihoods.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().intValue())) :
            nonSubscriptionWordLikelihoods.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().intValue()));
        
        for (TransactionFeatures tx : transactions) {
            String[] words = textPreprocessor.tokenize(tx.description());
            vocabulary.addAll(Arrays.asList(words));
            for (String word : words) {
                wordCounts.merge(word, 1, Integer::sum);
            }
        }
        
        int totalWords = wordCounts.values().stream().mapToInt(Integer::intValue).sum();
        Map<String, Double> updatedLikelihoods = new HashMap<>();
        for (String word : vocabulary) {
            int count = wordCounts.getOrDefault(word, 0);
            updatedLikelihoods.put(word, (count + ALPHA) / (totalWords + ALPHA * vocabulary.size()));
        }
        
        if (isSubscription) {
            subscriptionWordLikelihoods.clear();
            subscriptionWordLikelihoods.putAll(updatedLikelihoods);
        } else {
            nonSubscriptionWordLikelihoods.clear();
            nonSubscriptionWordLikelihoods.putAll(updatedLikelihoods);
        }
    }

    private void updateAmountDistributions(List<TransactionFeatures> transactions, boolean isSubscription) {
        List<Double> amounts = transactions.stream()
            .map(tx -> tx.amount().doubleValue())
            .collect(Collectors.toList());
        
        double mean, stdDev;
        if (isSubscription) {
            mean = amounts.isEmpty() ? subscriptionAmountDist.mean : 
                (subscriptionAmountDist.mean * (subscriptionCount - 1) + amounts.get(0)) / subscriptionCount;
            stdDev = calculateIncrementalStdDev(amounts, subscriptionAmountDist, subscriptionCount, true);
            subscriptionAmountDist = new GaussianDistribution(mean, stdDev);
        } else {
            mean = amounts.isEmpty() ? nonSubscriptionAmountDist.mean : 
                (nonSubscriptionAmountDist.mean * (nonSubscriptionCount - 1) + amounts.get(0)) / nonSubscriptionCount;
            stdDev = calculateIncrementalStdDev(amounts, nonSubscriptionAmountDist, nonSubscriptionCount, false);
            nonSubscriptionAmountDist = new GaussianDistribution(mean, stdDev);
        }
    }

    private double calculateIncrementalStdDev(List<Double> newAmounts, GaussianDistribution dist, int count, boolean isSub) {
        if (count <= 1) return MIN_STD_DEV;
        double mean = isSub ? dist.mean : dist.mean;
        double variance = newAmounts.stream()
            .mapToDouble(a -> Math.pow(a - mean, 2))
            .average()
            .orElse(MIN_STD_DEV);
        return Math.max(Math.sqrt(variance), MIN_STD_DEV);
    }

    private void updateTemporalLikelihoods(List<TransactionFeatures> transactions, boolean isSubscription) {
        Map<DayOfWeek, Integer> dayCounts = new HashMap<>();
        Map<Month, Integer> monthCounts = new HashMap<>();
        
        for (TransactionFeatures tx : transactions) {
            dayCounts.merge(tx.date().getDayOfWeek(), 1, Integer::sum);
            monthCounts.merge(tx.date().getMonth(), 1, Integer::sum);
        }
        
        int total = isSubscription ? subscriptionCount : nonSubscriptionCount;
        Map<DayOfWeek, Double> dayLikelihoods = new HashMap<>();
        Map<Month, Double> monthLikelihoods = new HashMap<>();
        
        for (DayOfWeek day : DayOfWeek.values()) {
            int count = dayCounts.getOrDefault(day, 0);
            dayLikelihoods.put(day, (count + ALPHA) / (total + 7 * ALPHA));
        }
        
        for (Month month : Month.values()) {
            int count = monthCounts.getOrDefault(month, 0);
            monthLikelihoods.put(month, (count + ALPHA) / (total + 12 * ALPHA));
        }
        
        if (isSubscription) {
            subscriptionDayLikelihoods.putAll(dayLikelihoods);
            subscriptionMonthLikelihoods.putAll(monthLikelihoods);
        } else {
            nonSubscriptionDayLikelihoods.putAll(dayLikelihoods);
            nonSubscriptionMonthLikelihoods.putAll(monthLikelihoods);
        }
    }

    private void updateAccountLikelihoods(List<TransactionFeatures> transactions, boolean isSubscription) {
        Map<Account.AccountType, Integer> typeCounts = new HashMap<>();
        Map<String, Integer> nameCounts = new HashMap<>();
        
        for (TransactionFeatures tx : transactions) {
            typeCounts.merge(tx.accountType(), 1, Integer::sum);
            nameCounts.merge(tx.accountName(), 1, Integer::sum);
        }
        
        int total = isSubscription ? subscriptionCount : nonSubscriptionCount;
        Map<Account.AccountType, Double> typeLikelihoods = new HashMap<>();
        Map<String, Double> nameLikelihoods = new HashMap<>();
        
        for (Account.AccountType type : Account.AccountType.values()) {
            int count = typeCounts.getOrDefault(type, 0);
            typeLikelihoods.put(type, (count + ALPHA) / (total + ALPHA * Account.AccountType.values().length));
        }
        
        Set<String> allNames = transactions.stream().map(TransactionFeatures::accountName).collect(Collectors.toSet());
        for (String name : allNames) {
            int count = nameCounts.getOrDefault(name, 0);
            nameLikelihoods.put(name, (count + ALPHA) / (total + ALPHA * allNames.size()));
        }
        
        if (isSubscription) {
            subscriptionAccountTypeLikelihoods.putAll(typeLikelihoods);
            subscriptionAccountNameLikelihoods.putAll(nameLikelihoods);
        } else {
            nonSubscriptionAccountTypeLikelihoods.putAll(typeLikelihoods);
            nonSubscriptionAccountNameLikelihoods.putAll(nameLikelihoods);
        }
    }
    
    // Helper class for Gaussian distribution calculations
    private static class GaussianDistribution {
        private double mean;
        private double stdDev;
        
        public GaussianDistribution(double mean, double stdDev) {
            this.mean = mean;
            this.stdDev = Math.max(stdDev, MIN_STD_DEV);
        }
        
        public double logProbability(double x) {
            double zScore = (x - mean) / stdDev;
            return -0.5 * (Math.log(2 * Math.PI) + 
                2 * Math.log(stdDev) + zScore * zScore);
        }
    }
    
    // Helper class for text preprocessing
    private static class TextPreprocessor {
        public String[] tokenize(String text) {
            return text.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", "")
                .split("\\s+");
        }
    }

    // Feature record
    public record TransactionFeatures(
        String description,
        BigDecimal amount,
        LocalDateTime date,
        String category,
        Account.AccountType accountType,
        String accountName
    ) {
        public static TransactionFeatures fromTransaction(
            org.emblow.envelofy.domain.Transaction tx
        ) {
            return new TransactionFeatures(
                tx.getDescription(),
                tx.getAmount(),
                tx.getDate(),
                tx.getEnvelope().getName(),
                tx.getAccount().getType(),
                tx.getAccount().getName()
            );
        }
    }
}