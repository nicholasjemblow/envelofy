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

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.emblow.envelofy.domain.Transaction;
import org.emblow.envelofy.domain.TransactionType;
import org.emblow.envelofy.domain.Envelope;
import org.emblow.envelofy.domain.Account;
import org.emblow.envelofy.repository.TransactionRepository;
import org.emblow.envelofy.repository.EnvelopeRepository;
import org.emblow.envelofy.repository.AccountRepository;
import org.emblow.envelofy.service.PatternService;

import java.io.BufferedReader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.emblow.envelofy.repository.AccountRepository;

@Service
public class CSVImportService {
    private final TransactionRepository transactionRepository;
    private final EnvelopeRepository envelopeRepository;
    private final AccountRepository accountRepository;
    private final PatternService patternService;
    private final TransactionService transactionService;

    public CSVImportService(
        TransactionRepository transactionRepository,
        EnvelopeRepository envelopeRepository,
        AccountRepository accountRepository,
        PatternService patternService,
        TransactionService transactionService
    ) {
        this.transactionRepository = transactionRepository;
        this.envelopeRepository = envelopeRepository;
        this.accountRepository = accountRepository;
        this.patternService = patternService;
        this.transactionService = transactionService;
    }

    @Transactional
    public ImportResult importTransactions(
        String csvContent,
        CSVMapping mapping,
        Long defaultEnvelopeId,
        Long accountId
    ) {
        ImportResult result = new ImportResult();
        
        Envelope defaultEnvelope = envelopeRepository.findById(defaultEnvelopeId)
            .orElseThrow(() -> new RuntimeException("Default envelope not found"));
            
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new RuntimeException("Account not found"));

        try (BufferedReader reader = new BufferedReader(new StringReader(csvContent))) {
            // Skip header row if specified
            if (mapping.hasHeader()) {
                reader.readLine();
            }

            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    String[] fields = line.split(mapping.delimiter());
                    
                    // Parse fields using mapping
                    LocalDateTime date = parseDate(
                        fields[mapping.dateColumnIndex()],
                        mapping.dateFormat()
                    );
                    
                    String description = fields[mapping.descriptionColumnIndex()].trim();
                    
                    // Determine the transaction type based on the multiplier:
                    // if negative => INCOME, otherwise EXPENSE.
                    TransactionType txType = mapping.amountMultiplier() < 0 ? 
                        TransactionType.INCOME : TransactionType.EXPENSE;
                    // Parse the amount using the absolute multiplier.
                    BigDecimal amount = parseAmount(
                        fields[mapping.amountColumnIndex()],
                        Math.abs(mapping.amountMultiplier())
                    );

                    // Find best envelope match using pattern service.
                    Transaction temp = new Transaction();
                    temp.setDate(date);
                    temp.setDescription(description);
                    temp.setAmount(amount);
                    temp.setAccount(account);
                    
                    Map<Envelope, Double> suggestions = patternService.suggestEnvelopes(temp);
                    
                    // Use highest confidence suggestion or default envelope.
                    Envelope targetEnvelope = suggestions.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .filter(e -> e.getValue() > 0.7) // Minimum confidence threshold
                        .map(Map.Entry::getKey)
                        .orElse(defaultEnvelope);

                    // Instead of manually constructing and saving the transaction,
                    // call recordTransaction to let it update balances appropriately.
                    transactionService.recordTransaction(
                        targetEnvelope.getId(),
                        account.getId(),
                        date,
                        description,
                        amount,
                        txType
                    );
                    
                    result.incrementSuccessful();
                    
                } catch (Exception e) {
                    result.addError("Error processing line: " + line + " - " + e.getMessage());
                    result.incrementFailed();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error processing CSV file: " + e.getMessage(), e);
        }

        return result;
    }

    private LocalDateTime parseDate(String value, String format) {
        return LocalDateTime.parse(value, DateTimeFormatter.ofPattern(format));
    }

    private BigDecimal parseAmount(String value, double multiplier) {
        // Remove currency symbols and spaces.
        String cleaned = value.replaceAll("[^\\d.-]", "");
        return new BigDecimal(cleaned).multiply(BigDecimal.valueOf(multiplier));
    }

    // Record to define CSV column mapping.
    public record CSVMapping(
        boolean hasHeader,
        String delimiter,
        int dateColumnIndex,
        String dateFormat,
        int descriptionColumnIndex,
        int amountColumnIndex,
        double amountMultiplier
    ) {
        // Predefined mappings for common bank formats.
        public static CSVMapping CHASE = new CSVMapping(
            true, ",", 0, "MM/dd/yyyy", 2, 3, 1.0
        );
        
        public static CSVMapping BANK_OF_AMERICA = new CSVMapping(
            true, ",", 0, "MM/dd/yyyy", 1, 2, 1.0
        );
        
        // Add more presets as needed.
    }

    // Class to hold import results.
    public static class ImportResult {
        private int successful = 0;
        private int failed = 0;
        private List<String> errors = new ArrayList<>();

        public void incrementSuccessful() { successful++; }
        public void incrementFailed() { failed++; }
        public void addError(String error) { errors.add(error); }

        public int getSuccessful() { return successful; }
        public int getFailed() { return failed; }
        public List<String> getErrors() { return errors; }
    }
}
