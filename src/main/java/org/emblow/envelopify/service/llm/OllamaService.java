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
package org.emblow.envelopify.service.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import org.emblow.envelopify.service.*;
import org.emblow.envelopify.domain.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import org.emblow.envelopify.service.ml.SpendingInsight;
import org.emblow.envelopify.service.ml.SpendingInsightService;
import org.emblow.envelopify.service.ml.SpendingInsightType;
import org.emblow.envelopify.service.ml.AdvancedMLService;
import org.springframework.web.client.RestClientException;

@Service
public class OllamaService implements LLMService{
    private final String OLLAMA_URL = "http://localhost:11434/api/generate";
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    private final TransactionService transactionService;
    private final EnvelopeService envelopeService;
    private final SpendingInsightService insightService;
    private final PatternService patternService;
    private final AdvancedMLService advancedMLService;  // New dependency

    public OllamaService(
        TransactionService transactionService,
        EnvelopeService envelopeService,
        SpendingInsightService insightService,
        PatternService patternService,
        AdvancedMLService advancedMLService   // Inject the AdvancedMLService
    ) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.transactionService = transactionService;
        this.envelopeService = envelopeService;
        this.insightService = insightService;
        this.patternService = patternService;
        this.advancedMLService = advancedMLService;
    }

    @Override
    public String processUserQuery(String userQuery) {
        try {
            String context = buildFinancialContext();
            String fullPrompt = buildPrompt(userQuery, context);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", "nemotron-mini");
            requestBody.put("prompt", fullPrompt);
            requestBody.put("stream", false);

            HttpEntity<String> request = new HttpEntity<>(requestBody.toString(), headers);
            ResponseEntity<String> response = restTemplate.postForEntity(OLLAMA_URL, request, String.class);
            
            // Parse the JSON response
            ObjectNode responseJson = (ObjectNode) objectMapper.readTree(response.getBody());
            return responseJson.get("response").asText();
        } catch (JsonProcessingException | RestClientException e) {
            throw new RuntimeException("Error communicating with Ollama: " + e.getMessage(), e);
        }
    }

    private String buildFinancialContext() {
        StringBuilder context = new StringBuilder();
        
        // Get current envelope information
        List<Envelope> envelopes = envelopeService.getAllEnvelopes();
        context.append("Current Envelopes:\n");
        for (Envelope env : envelopes) {
            context.append(String.format("- %s: Budget $%.2f, Available $%.2f\n",
                env.getName(),
                env.getMonthlyBudget(),
                env.getAvailable()));
        }

        // Get recent transactions
        LocalDateTime oneMonthAgo = LocalDateTime.now().minusMonths(1);
        List<Transaction> recentTransactions = transactionService.getRecentTransactions(
            oneMonthAgo,
            LocalDateTime.now()
        );
        
        context.append("\nRecent Transactions:\n");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        for (Transaction tx : recentTransactions.stream().limit(10).collect(Collectors.toList())) {
            context.append(String.format("- %s: $%.2f in %s (%s)\n",
                tx.getDate().format(formatter),
                tx.getAmount(),
                tx.getEnvelope().getName(),
                tx.getDescription()));
        }

        // Merge regular insights with advanced ML insights
        List<SpendingInsight> regularInsights = insightService.generateInsights();
        List<SpendingInsight> advancedInsights = getAdvancedMLInsights();
        List<SpendingInsight> allInsights = new ArrayList<>();
        allInsights.addAll(regularInsights);
        allInsights.addAll(advancedInsights);
        
        context.append("\nFinancial Insights:\n");
        for (SpendingInsight insight : allInsights) {
            context.append("- ").append(insight.getMessage()).append("\n");
        }

        // Calculate overall financial status
        BigDecimal totalBudget = envelopes.stream()
            .map(Envelope::getMonthlyBudget)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
            
        BigDecimal totalAvailable = envelopes.stream()
            .map(Envelope::getAvailable)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
            
        context.append(String.format("\nOverall Status:\n- Total Budget: $%.2f\n- Total Available: $%.2f\n",
            totalBudget, totalAvailable));

        // Add spending patterns for recent transactions
        if (!recentTransactions.isEmpty()) {
            context.append("\nSpending Patterns:\n");
            for (Transaction tx : recentTransactions.stream().limit(5).collect(Collectors.toList())) {
                Map<Envelope, Double> suggestions = patternService.suggestEnvelopes(tx);
                if (!suggestions.isEmpty()) {
                    context.append(String.format("- Transaction '%s' matches patterns from: ", tx.getDescription()));
                    context.append(suggestions.entrySet().stream()
                        .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                        .limit(2)
                        .map(e -> String.format("%s (%.0f%% confidence)", 
                            e.getKey().getName(), 
                            e.getValue() * 100))
                        .collect(Collectors.joining(", ")))
                        .append("\n");
                }
            }
        }

        return context.toString();
    }

    private String buildPrompt(String userQuery, String context) {
        return String.format("""
            Role: You are a financial analysis assistant with direct access to the user's financial data.
            Task: Analyze the provided financial data and answer the user's question with specific, data-driven insights.
            
            Available Data:
            %s
            
            Functions Available:
            - Calculate spending trends across envelopes
            - Compare current spending to budget allocations
            - Identify unusual transactions or patterns
            - Project future expenses based on historical data
            
            User Question: %s
            
            Instructions:
            1. Focus on specific numbers and data points from the provided context
            2. Show brief calculations when relevant
            3. Reference specific transactions or patterns
            4. Provide actionable recommendations based on the data
            5. Keep responses clear and direct
            
            Response Format:
            - Start with a direct answer to the question
            - Include relevant data points and calculations
            - End with a specific, actionable recommendation if applicable
            """, context, userQuery);
    }
    
    /**
     * Helper method that retrieves advanced ML analysis and maps it into SpendingInsight objects.
     */
    private List<SpendingInsight> getAdvancedMLInsights() {
        List<AdvancedMLService.AccountAnalysis> analyses = advancedMLService.analyzeAccounts();
        List<SpendingInsight> mlInsights = new ArrayList<>();
        
        for (AdvancedMLService.AccountAnalysis analysis : analyses) {
            // --- Map anomalies ---
            for (AdvancedMLService.AnomalyDetection anomaly : analysis.getAnomalies()) {
                if (anomaly.type() == AdvancedMLService.AnomalyType.AMOUNT) {
                    mlInsights.add(new SpendingInsight(
                        SpendingInsightType.UNUSUAL_SPENDING,
                        anomaly.description(),
                        anomaly.severity()  // Use anomaly severity as confidence
                    ));
                } else if (anomaly.type() == AdvancedMLService.AnomalyType.FREQUENCY) {
                    mlInsights.add(new SpendingInsight(
                        SpendingInsightType.RECURRING_PAYMENT,
                        anomaly.description(),
                        anomaly.severity()
                    ));
                }
            }
            
            // --- Map recurring payments from merchant metrics ---
            analysis.getMerchantMetrics().forEach((merchant, metrics) -> {
                if (metrics.monthlyFrequency() >= 1.0) { // Arbitrary threshold; adjust as needed.
                    mlInsights.add(new SpendingInsight(
                        SpendingInsightType.RECURRING_PAYMENT,
                        "Recurring payment detected at " + merchant + " (monthly frequency: " +
                            String.format("%.1f", metrics.monthlyFrequency()) + ")",
                        Math.min(1.0, metrics.monthlyFrequency() / 5.0)
                    ));
                }
            });
            
            // --- Map predicted expense from overall spending trend ---
            if (analysis.getVolumeTrend() > 0.2) {
                mlInsights.add(new SpendingInsight(
                    SpendingInsightType.PREDICTED_EXPENSE,
                    "Your spending is trending upward for account " + analysis.getAccount().getName() +
                        ". Consider revising your budget.",
                    0.8
                ));
            }
            
            // --- Map budget suggestions from envelope metrics ---
            analysis.getEnvelopeMetrics().forEach((envelope, envMetrics) -> {
                if (envMetrics.budgetUtilization() > 0.9) {
                    mlInsights.add(new SpendingInsight(
                        SpendingInsightType.BUDGET_SUGGESTION,
                        "Your envelope " + envelope.getName() + " is at " +
                            String.format("%.0f%%", envMetrics.budgetUtilization() * 100) +
                            " of its budget. Consider adjusting your budget.",
                        envMetrics.budgetUtilization()
                    ));
                }
            });
            
            // --- Map seasonal patterns ---
            if (analysis.getVolumeTrend() < -0.2) {
                mlInsights.add(new SpendingInsight(
                    SpendingInsightType.SEASONAL_PATTERN,
                    "Your spending trend indicates a seasonal downturn for account " +
                        analysis.getAccount().getName() + ".",
                    0.7
                ));
            }
            
            // --- Map reallocation suggestions from cross-account analysis ---
            AdvancedMLService.CrossAccountMetrics crossMetrics = analysis.getCrossAccountMetrics();
            if (crossMetrics != null && !crossMetrics.sharedMerchants().isEmpty()) {
                mlInsights.add(new SpendingInsight(
                    SpendingInsightType.REALLOCATION_SUGGESTION,
                    "Your spending patterns share similarities with other accounts. Consider reallocation of funds.",
                    0.75
                ));
            }
        }
        return mlInsights;
    }
}
