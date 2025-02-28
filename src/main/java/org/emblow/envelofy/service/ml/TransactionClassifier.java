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
import org.emblow.envelofy.repository.EnvelopeRepository;
import org.emblow.envelofy.service.ml.TransactionNaiveBayes.TransactionFeatures;

import java.time.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.math.BigDecimal;

@Service
public class TransactionClassifier {
    private static final Logger log = LoggerFactory.getLogger(TransactionClassifier.class);
    
    private final TransactionRepository transactionRepository;
    private final EnvelopeRepository envelopeRepository;
    private final TransactionNaiveBayes classifier;
    
    // Cache of account-specific models
    private final Map<Account.AccountType, TransactionNaiveBayes> accountTypeModels = new HashMap<>();
    private final Map<String, TransactionNaiveBayes> accountSpecificModels = new HashMap<>();

    public TransactionClassifier(
        TransactionRepository transactionRepository,
        EnvelopeRepository envelopeRepository
    ) {
        this.transactionRepository = transactionRepository;
        this.envelopeRepository = envelopeRepository;
        this.classifier = new TransactionNaiveBayes();
    }

    public void trainModel() {
        List<Transaction> transactions = transactionRepository.findAll();
        if (transactions.isEmpty()) {
            log.warn("No transactions available for training");
            return;
        }

        // Train main classifier
        List<TransactionFeatures> features = transactions.stream()
            .map(TransactionFeatures::fromTransaction)
            .toList();
        classifier.train(features);
        
        // Train account type specific models
        for (Account.AccountType accountType : Account.AccountType.values()) {
            List<Transaction> accountTypeTransactions = transactions.stream()
                .filter(tx -> tx.getAccount().getType() == accountType)
                .toList();
                
            if (!accountTypeTransactions.isEmpty()) {
                TransactionNaiveBayes typeModel = new TransactionNaiveBayes();
                typeModel.train(accountTypeTransactions.stream()
                    .map(TransactionFeatures::fromTransaction)
                    .toList());
                accountTypeModels.put(accountType, typeModel);
            }
        }
        
        // Train account-specific models
        Set<String> accountNames = transactions.stream()
            .map(tx -> tx.getAccount().getName())
            .collect(java.util.stream.Collectors.toSet());
            
        for (String accountName : accountNames) {
            List<Transaction> accountTransactions = transactions.stream()
                .filter(tx -> tx.getAccount().getName().equals(accountName))
                .toList();
                
            if (accountTransactions.size() >= 50) { // Only train if enough data
                TransactionNaiveBayes accountModel = new TransactionNaiveBayes();
                accountModel.train(accountTransactions.stream()
                    .map(TransactionFeatures::fromTransaction)
                    .toList());
                accountSpecificModels.put(accountName, accountModel);
            }
        }
        
        log.info("Model trained with {} transactions", transactions.size());
    }

    public Map<Envelope, Double> predictCategory(Transaction transaction) {
        TransactionFeatures features = TransactionFeatures.fromTransaction(transaction);
        
        // Get predictions from different models
        Map<String, Double> generalPredictions = classifier.predict(features);
        Map<String, Double> typeSpecificPredictions = accountTypeModels
            .getOrDefault(transaction.getAccount().getType(), classifier)
            .predict(features);
        Map<String, Double> accountSpecificPredictions = accountSpecificModels
            .getOrDefault(transaction.getAccount().getName(), classifier)
            .predict(features);
            
        // Weighted combination of predictions
        Map<String, Double> combinedPredictions = new HashMap<>();
        Set<String> allCategories = new HashSet<>();
        allCategories.addAll(generalPredictions.keySet());
        allCategories.addAll(typeSpecificPredictions.keySet());
        allCategories.addAll(accountSpecificPredictions.keySet());
        
        for (String category : allCategories) {
            double generalWeight = 0.4;
            double typeWeight = 0.3;
            double accountWeight = 0.3;
            
            double combinedScore = 
                generalWeight * generalPredictions.getOrDefault(category, 0.0) +
                typeWeight * typeSpecificPredictions.getOrDefault(category, 0.0) +
                accountWeight * accountSpecificPredictions.getOrDefault(category, 0.0);
                
            combinedPredictions.put(category, combinedScore);
        }
        
        // Normalize combined scores
        double totalScore = combinedPredictions.values().stream()
            .mapToDouble(Double::doubleValue)
            .sum();
            
        Map<String, Double> normalizedPredictions = new HashMap<>();
        combinedPredictions.forEach((category, score) -> 
            normalizedPredictions.put(category, score / totalScore));
        
        // Convert category names to envelopes
        Map<Envelope, Double> predictions = new HashMap<>();
        normalizedPredictions.forEach((categoryName, probability) -> {
            if (probability > 0.05) { // Only include non-trivial probabilities
                transactionRepository.findByEnvelopeName(categoryName)
                    .stream()
                    .findFirst()
                    .ifPresent(tx -> predictions.put(tx.getEnvelope(), probability));
            }
        });
        
        return predictions;
    }

    public Map<Envelope, Double> suggestEnvelopesForAccount(Account account) {
        // Get recent transactions for this account
        LocalDateTime oneMonthAgo = LocalDateTime.now().minusMonths(1);
        List<Transaction> accountTransactions = transactionRepository.findAll().stream()
            .filter(tx -> tx.getAccount().getId().equals(account.getId()))
            .filter(tx -> tx.getDate().isAfter(oneMonthAgo))
            .toList();
            
        if (accountTransactions.isEmpty()) {
            return Map.of();
        }
        
        // Calculate envelope usage frequencies
        Map<Envelope, Integer> envelopeCounts = new HashMap<>();
        Map<Envelope, BigDecimal> envelopeAmounts = new HashMap<>();
        
        for (Transaction tx : accountTransactions) {
            envelopeCounts.merge(tx.getEnvelope(), 1, Integer::sum);
            envelopeAmounts.merge(tx.getEnvelope(), tx.getAmount(), BigDecimal::add);
        }
        
        // Calculate scores based on both frequency and amount
        Map<Envelope, Double> scores = new HashMap<>();
        double totalTransactions = accountTransactions.size();
        BigDecimal totalAmount = envelopeAmounts.values().stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);
            
        envelopeCounts.forEach((envelope, count) -> {
            double frequencyScore = count / totalTransactions;
            double amountScore = envelopeAmounts.get(envelope)
                .divide(totalAmount, 2, java.math.RoundingMode.HALF_UP)
                .doubleValue();
            scores.put(envelope, 0.5 * frequencyScore + 0.5 * amountScore);
        });
        
        return scores;
    }

    public void retrain() {
        accountTypeModels.clear();
        accountSpecificModels.clear();
        trainModel();
    }
}