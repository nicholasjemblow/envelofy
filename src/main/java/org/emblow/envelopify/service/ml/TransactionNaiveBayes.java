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
import java.util.*;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.Month;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.emblow.envelopify.domain.Account;
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
        this.textPreprocessor = new TextPreprocessor();
    }
    
    public void train(List<TransactionFeatures> transactions) {
        log.info("Training NBC with {} transactions", transactions.size());
        
        // Clear previous state
        categoryPriors.clear();
        wordLikelihoods.clear();
        amountDistributions.clear();
        dayOfWeekLikelihoods.clear();
        monthLikelihoods.clear();
        accountTypeLikelihoods.clear();
        accountNameLikelihoods.clear();
        vocabulary.clear();
        
        // Count category frequencies
        Map<String, Integer> categoryCounts = new HashMap<>();
        for (TransactionFeatures tx : transactions) {
            categoryCounts.merge(tx.category(), 1, Integer::sum);
        }
        
        // Calculate priors
        int totalTransactions = transactions.size();
        categoryCounts.forEach((category, count) -> 
            categoryPriors.put(category, (count.doubleValue() + ALPHA) / 
                (totalTransactions + ALPHA * categoryCounts.size())));
        
        // Build vocabulary
        transactions.forEach(tx -> 
            vocabulary.addAll(Arrays.asList(textPreprocessor.tokenize(tx.description()))));
        
        // Initialize likelihood tables
        for (String category : categoryCounts.keySet()) {
            wordLikelihoods.put(category, new HashMap<>());
            dayOfWeekLikelihoods.put(category, new HashMap<>());
            monthLikelihoods.put(category, new HashMap<>());
            accountTypeLikelihoods.put(category, new HashMap<>());
            accountNameLikelihoods.put(category, new HashMap<>());
        }
        
        // Calculate all likelihoods
        calculateWordLikelihoods(transactions, categoryCounts);
        calculateAmountDistributions(transactions, categoryCounts);
        calculateTemporalLikelihoods(transactions, categoryCounts);
        calculateAccountLikelihoods(transactions, categoryCounts);
        
        log.info("Training complete. Vocabulary size: {}", vocabulary.size());
    }
    
    public Map<String, Double> predict(TransactionFeatures transaction) {
        Map<String, Double> scores = new HashMap<>();
        double totalScore = 0.0;
        
        // Calculate scores for each category
        for (String category : categoryPriors.keySet()) {
            double score = Math.log(categoryPriors.get(category));
            
            // Word features
            String[] words = textPreprocessor.tokenize(transaction.description());
            double wordScore = 0.0;
            for (String word : words) {
                if (vocabulary.contains(word)) {
                    wordScore += Math.log(wordLikelihoods.get(category)
                        .getOrDefault(word, ALPHA / (vocabulary.size() * ALPHA)));
                }
            }
            
            // Amount feature
            double amountScore = amountDistributions.get(category)
                .logProbability(transaction.amount().doubleValue());
            
            // Temporal features
            double dayScore = Math.log(dayOfWeekLikelihoods.get(category)
                .getOrDefault(transaction.date().getDayOfWeek(), 
                    ALPHA / (7 * ALPHA)));
            double monthScore = Math.log(monthLikelihoods.get(category)
                .getOrDefault(transaction.date().getMonth(), 
                    ALPHA / (12 * ALPHA)));
                    
            // Account features
            double accountTypeScore = Math.log(accountTypeLikelihoods.get(category)
                .getOrDefault(transaction.accountType(), 
                    ALPHA / (Account.AccountType.values().length * ALPHA)));
                    
            double accountNameScore = Math.log(accountNameLikelihoods.get(category)
                .getOrDefault(transaction.accountName(), 
                    ALPHA / accountNameLikelihoods.size()));
            
            // Combine scores with weights
            score += WORD_WEIGHT * wordScore +
                    AMOUNT_WEIGHT * amountScore +
                    TIME_WEIGHT * (dayScore + monthScore) +
                    ACCOUNT_TYPE_WEIGHT * accountTypeScore +
                    ACCOUNT_NAME_WEIGHT * accountNameScore;
            
            scores.put(category, Math.exp(score));
            totalScore += Math.exp(score);
        }
        
        // Normalize probabilities
        Map<String, Double> normalizedScores = new HashMap<>();
        for (Map.Entry<String, Double> entry : scores.entrySet()) {
            normalizedScores.put(entry.getKey(), entry.getValue() / totalScore);
        }
        
        return normalizedScores;
    }
    
    private void calculateWordLikelihoods(
        List<TransactionFeatures> transactions,
        Map<String, Integer> categoryCounts
    ) {
        // Count word occurrences per category
        Map<String, Map<String, Integer>> wordCounts = new HashMap<>();
        categoryCounts.keySet().forEach(category -> 
            wordCounts.put(category, new HashMap<>()));
        
        for (TransactionFeatures tx : transactions) {
            String[] words = textPreprocessor.tokenize(tx.description());
            for (String word : words) {
                wordCounts.get(tx.category()).merge(word, 1, Integer::sum);
            }
        }
        
        // Calculate likelihoods with Laplace smoothing
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
        // Count account patterns per category
        Map<String, Map<Account.AccountType, Integer>> typeCountsByCategory = new HashMap<>();
        Map<String, Map<String, Integer>> nameCountsByCategory = new HashMap<>();
        
        for (TransactionFeatures tx : transactions) {
            // Account type counts
            typeCountsByCategory
                .computeIfAbsent(tx.category(), k -> new HashMap<>())
                .merge(tx.accountType(), 1, Integer::sum);
                
            // Account name counts
            nameCountsByCategory
                .computeIfAbsent(tx.category(), k -> new HashMap<>())
                .merge(tx.accountName(), 1, Integer::sum);
        }
        
        // Calculate likelihoods with smoothing
        for (String category : categoryCounts.keySet()) {
            int totalTx = categoryCounts.get(category);
            
            // Account type likelihoods
            for (Account.AccountType type : Account.AccountType.values()) {
                int count = typeCountsByCategory
                    .getOrDefault(category, Map.of())
                    .getOrDefault(type, 0);
                    
                double likelihood = (count + ALPHA) / 
                    (totalTx + ALPHA * Account.AccountType.values().length);
                    
                accountTypeLikelihoods.get(category).put(type, likelihood);
            }
            
            // Account name likelihoods
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
        // Group amounts by category
        Map<String, List<Double>> amountsByCategory = new HashMap<>();
        categoryCounts.keySet().forEach(category -> 
            amountsByCategory.put(category, new ArrayList<>()));
        
        transactions.forEach(tx -> 
            amountsByCategory.get(tx.category())
                .add(tx.amount().doubleValue()));
        
        // Calculate distributions
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
        // Count temporal patterns
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
        
        // Calculate likelihoods with Laplace smoothing
        for (String category : categoryCounts.keySet()) {
            // Days of week
            int totalDays = dayCountsByCategory.get(category).values().stream()
                .mapToInt(Integer::intValue).sum();
            
            for (DayOfWeek day : DayOfWeek.values()) {
                int count = dayCountsByCategory.get(category)
                    .getOrDefault(day, 0);
                double likelihood = (count + ALPHA) / (totalDays + 7 * ALPHA);
                dayOfWeekLikelihoods.get(category).put(day, likelihood);
            }
            
            // Months
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
    
    // Helper class for Gaussian distribution calculations
    private static class GaussianDistribution {
        private final double mean;
        private final double stdDev;
        
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
            org.emblow.envelopify.domain.Transaction tx
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