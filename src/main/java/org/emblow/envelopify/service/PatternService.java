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
import org.emblow.envelopify.domain.Transaction;
import org.emblow.envelopify.domain.Envelope;
import org.emblow.envelopify.repositry.TransactionRepository;
import org.emblow.envelopify.repositry.EnvelopeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@Service
public class PatternService {
    private final TransactionRepository transactionRepository;
    private final EnvelopeRepository envelopeRepository;

    public PatternService(
        TransactionRepository transactionRepository,
        EnvelopeRepository envelopeRepository
    ) {
        this.transactionRepository = transactionRepository;
        this.envelopeRepository = envelopeRepository;
    }

    /**
     * Suggests envelopes for a transaction based on historical patterns.
     * Returns a map of envelopes to confidence scores (0-1).
     */
    public Map<Envelope, Double> suggestEnvelopes(Transaction transaction) {
        Map<Envelope, Double> suggestions = new HashMap<>();
        double totalScore = 0.0;

        // Get all recent transactions
        LocalDateTime oneMonthAgo = LocalDateTime.now().minusMonths(1);
        List<Transaction> recentTransactions = transactionRepository
            .findByDateBetweenOrderByDateDesc(oneMonthAgo, LocalDateTime.now());

        // Score each envelope based on merchant name matches
        for (Envelope envelope : envelopeRepository.findAll()) {
            double score = scoreEnvelopeMatch(transaction, envelope, recentTransactions);
            if (score > 0) {
                suggestions.put(envelope, score);
                totalScore += score;
            }
        }

        // Normalize scores
        if (totalScore > 0) {
            for (Map.Entry<Envelope, Double> entry : suggestions.entrySet()) {
                entry.setValue(entry.getValue() / totalScore);
            }
        }

        return suggestions;
    }

    private double scoreEnvelopeMatch(
        Transaction newTransaction,
        Envelope envelope,
        List<Transaction> recentTransactions
    ) {
        double score = 0.0;

        // Find similar transactions in this envelope
        List<Transaction> envelopeTransactions = recentTransactions.stream()
            .filter(t -> t.getEnvelope().getId().equals(envelope.getId()))
            .toList();

        for (Transaction past : envelopeTransactions) {
            // Score based on description similarity
            if (isSimilarDescription(newTransaction.getDescription(), past.getDescription())) {
                score += 1.0;
            }

            // Score based on amount similarity
            if (isSimilarAmount(newTransaction.getAmount(), past.getAmount())) {
                score += 0.5;
            }

            // Score based on timing (day of week/month)
            if (isSimilarTiming(newTransaction.getDate(), past.getDate())) {
                score += 0.25;
            }
        }

        return score;
    }

    private boolean isSimilarDescription(String desc1, String desc2) {
        if (desc1 == null || desc2 == null) {
            return false;
        }
        return desc1.toLowerCase().contains(desc2.toLowerCase()) ||
               desc2.toLowerCase().contains(desc1.toLowerCase());
    }

    private boolean isSimilarAmount(BigDecimal amount1, BigDecimal amount2) {
        if (amount1 == null || amount2 == null) {
            return false;
        }
        // Consider amounts within 10% of each other similar
        BigDecimal diff = amount1.subtract(amount2).abs();
        BigDecimal threshold = amount1.multiply(new BigDecimal("0.1"));
        return diff.compareTo(threshold) <= 0;
    }

    private boolean isSimilarTiming(LocalDateTime date1, LocalDateTime date2) {
        if (date1 == null || date2 == null) {
            return false;
        }
        return date1.getDayOfWeek() == date2.getDayOfWeek() ||
               date1.getDayOfMonth() == date2.getDayOfMonth();
    }
}
