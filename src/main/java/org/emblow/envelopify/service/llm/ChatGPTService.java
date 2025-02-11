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

/**
 * NB To use, you will need add in your api keys etc:
 * openai.api.key=sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
 * openai.chat.endpoint=https://api.openai.com/v1/chat/completions
 * 
 * @author Nicholas J Emblow
 */
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.emblow.envelopify.domain.Envelope;
import org.emblow.envelopify.domain.Transaction;
import org.emblow.envelopify.service.EnvelopeService;
import org.emblow.envelopify.service.PatternService;
import org.emblow.envelopify.service.TransactionService;
import org.emblow.envelopify.service.ml.SpendingInsight;
import org.emblow.envelopify.service.ml.SpendingInsightService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ChatGPTService implements LLMService {

    // OpenAI Chat API endpoint.
    private final String CHATGPT_URL = "https://api.openai.com/v1/chat/completions";
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    // Financial context dependencies.
    private final TransactionService transactionService;
    private final EnvelopeService envelopeService;
    private final SpendingInsightService insightService;
    private final PatternService patternService;
    
    // API key injected via configuration.
    @Value("${openai.api.key}")
    private String openaiApiKey;

    public ChatGPTService(TransactionService transactionService,
                          EnvelopeService envelopeService,
                          SpendingInsightService insightService,
                          PatternService patternService) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.transactionService = transactionService;
        this.envelopeService = envelopeService;
        this.insightService = insightService;
        this.patternService = patternService;
    }
    
    /**
     * Processes the user query by building a financial context, assembling a prompt, and then sending
     * the request to ChatGPT for a response.
     *
     * @param userQuery The user's question.
     * @return The response text from ChatGPT.
     */
    public String processUserQuery(String userQuery) {
        try {
            String context = buildFinancialContext();
            String fullPrompt = buildPrompt(userQuery, context);
            
            // Build the JSON request body following OpenAI's Chat API format.
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", "gpt-3.5-turbo");
            requestBody.put("temperature", 0.0);
            requestBody.put("stream", false);
            
            // Construct the messages array.
            ArrayNode messages = objectMapper.createArrayNode();
            
            // System message to set the assistant's role.
            ObjectNode systemMessage = objectMapper.createObjectNode();
            systemMessage.put("role", "system");
            systemMessage.put("content", "You are a financial analysis assistant with direct access to the user's financial data.");
            messages.add(systemMessage);
            
            // User message with the combined prompt.
            ObjectNode userMessage = objectMapper.createObjectNode();
            userMessage.put("role", "user");
            userMessage.put("content", fullPrompt);
            messages.add(userMessage);
            
            requestBody.set("messages", messages);
            
            // Set up HTTP headers with content type and authorization.
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openaiApiKey);
            
            HttpEntity<String> entity = new HttpEntity<>(requestBody.toString(), headers);
            ResponseEntity<String> response = restTemplate.postForEntity(CHATGPT_URL, entity, String.class);
            
            // Parse the response to extract the message content.
            JsonNode root = objectMapper.readTree(response.getBody());
            if (root.has("choices") && root.get("choices").isArray() && root.get("choices").size() > 0) {
                return root.get("choices").get(0).get("message").get("content").asText();
            }
            return "No response received from ChatGPT.";
        } catch (Exception e) {
            throw new RuntimeException("Error during ChatGPT inference: " + e.getMessage(), e);
        }
    }
    
    /**
     * Builds the financial context string by collecting current envelope info, recent transactions,
     * insights, overall status, and spending patterns.
     *
     * @return A string representing the current financial context.
     */
    private String buildFinancialContext() {
        StringBuilder context = new StringBuilder();
        
        // Current envelope information.
        List<Envelope> envelopes = envelopeService.getAllEnvelopes();
        context.append("Current Envelopes:\n");
        for (Envelope env : envelopes) {
            context.append(String.format("- %s: Budget $%.2f, Available $%.2f\n",
                    env.getName(),
                    env.getMonthlyBudget(),
                    env.getAvailable()));
        }
        
        // Recent transactions.
        LocalDateTime oneMonthAgo = LocalDateTime.now().minusMonths(1);
        List<Transaction> recentTransactions = transactionService.getRecentTransactions(oneMonthAgo, LocalDateTime.now());
        context.append("\nRecent Transactions:\n");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        for (Transaction tx : recentTransactions.stream().limit(10).collect(Collectors.toList())) {
            context.append(String.format("- %s: $%.2f in %s (%s)\n",
                    tx.getDate().format(formatter),
                    tx.getAmount(),
                    tx.getEnvelope().getName(),
                    tx.getDescription()));
        }
        
        // Financial insights.
        List<SpendingInsight> regularInsights = insightService.generateInsights();
        context.append("\nFinancial Insights:\n");
        for (SpendingInsight insight : regularInsights) {
            context.append("- ").append(insight.getMessage()).append("\n");
        }
        
        // Overall financial status.
        BigDecimal totalBudget = envelopes.stream()
                .map(Envelope::getMonthlyBudget)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalAvailable = envelopes.stream()
                .map(Envelope::getAvailable)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        context.append(String.format("\nOverall Status:\n- Total Budget: $%.2f\n- Total Available: $%.2f\n",
                totalBudget, totalAvailable));
        
        // Spending patterns.
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
    
    /**
     * Builds the prompt by combining the financial context and the user's query.
     *
     * @param userQuery The user's question.
     * @param context   The financial context string.
     * @return The full prompt to be sent to ChatGPT.
     */
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
                1. Focus on specific numbers and data points from the provided context.
                2. Show brief calculations when relevant.
                3. Reference specific transactions or patterns.
                4. Provide actionable recommendations based on the data.
                5. Keep responses clear and direct.
                
                Response Format:
                - Start with a direct answer to the question.
                - Include relevant data points and calculations.
                - End with a specific, actionable recommendation if applicable.
                """, context, userQuery);
    }
}


