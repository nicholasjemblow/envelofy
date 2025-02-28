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
package org.emblow.envelofy.service.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.emblow.envelofy.service.AccountService;
import org.emblow.envelofy.service.EnvelopeService;
import org.emblow.envelofy.service.PatternService;
import org.emblow.envelofy.service.TransactionService;
import org.emblow.envelofy.service.IntentDetectionService;
import org.emblow.envelofy.service.ml.AdvancedMLService;
import org.emblow.envelofy.service.ml.SpendingInsightService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

public class GroqService extends AbstractLLMService {
    private final String groqApiKey;
    private final String groqBaseUrl;
    private final String groqModel;
    private final WebClient webClient;

    public GroqService(
            TransactionService transactionService,
            EnvelopeService envelopeService,
            SpendingInsightService insightService,
            PatternService patternService,
            AdvancedMLService advancedMLService,
            IntentDetectionService intentDetectionService,
            AccountService accountService,
            String groqApiKey,
            String groqBaseUrl,
            String groqModel) {
        super(transactionService, envelopeService, insightService,
              patternService, advancedMLService, intentDetectionService,
              accountService);
        this.groqApiKey = groqApiKey;
        this.groqBaseUrl = groqBaseUrl;
        this.groqModel = groqModel;
        this.webClient = WebClient.builder()
            .baseUrl(groqBaseUrl)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + groqApiKey)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    @Override
    public String processUserQuery(String userQuery) {
        return super.processUserQuery(userQuery); // Delegate to AbstractLLMService
    }

    @Override
    protected String callLLM(String prompt) {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", groqModel);
            requestBody.put("temperature", 0.0);
            requestBody.put("stream", false);
            requestBody.put("top_p", 1.0);
            requestBody.put("frequency_penalty", 0.0);
            requestBody.put("presence_penalty", 0.0);

            ArrayNode messages = objectMapper.createArrayNode();
            messages.add(createMessage("system", "You are a financial analysis assistant with direct access to the user's financial data."));
            messages.add(createMessage("user", prompt));
            requestBody.set("messages", messages);

            String responseBody = webClient.post()
                .uri("/openai/v1/chat/completions")
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(status -> status.value() != 200,
                    response -> response.bodyToMono(String.class)
                        .map(body -> new RuntimeException("Groq API error: " + body)))
                .bodyToMono(String.class)
                .block();

            JsonNode root = objectMapper.readTree(responseBody);
            if (root.has("choices") && root.get("choices").isArray() && root.get("choices").size() > 0) {
                return root.get("choices").get(0).get("message").get("content").asText();
            }
            return "No response received from Groq.";
        } catch (Exception e) {
            throw new RuntimeException("Error during Groq inference: " + e.getMessage(), e);
        }
    }

    @Override
    protected Flux<String> doStreamUserQuery(String userQuery) {
        String intent = intentDetectionService.detectIntent(userQuery);
        String context = buildFinancialContext();
        String fullPrompt = buildPrompt(userQuery, context);

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", groqModel);
        requestBody.put("temperature", 0.0);
        requestBody.put("stream", false);
        requestBody.put("top_p", 1.0);
        requestBody.put("frequency_penalty", 0.0);
        requestBody.put("presence_penalty", 0.0);

        ArrayNode messages = objectMapper.createArrayNode();
        messages.add(createMessage("system", "You are a financial analysis assistant with direct access to the user's financial data."));
        messages.add(createMessage("user", fullPrompt));
        requestBody.set("messages", messages);

        return webClient.post()
            .uri("/openai/v1/chat/completions")
            .bodyValue(requestBody)
            .retrieve()
            .bodyToFlux(String.class)
            .map(chunk -> {
                try {
                    if (chunk.trim().equals("DONE")) {
                        return "";
                    }

                    JsonNode node = objectMapper.readTree(chunk);
                    if (node.has("choices") && node.get("choices").size() > 0) {
                        JsonNode delta = node.get("choices").get(0).get("delta");
                        if (delta.has("content")) {
                            return delta.get("content").asText();
                        }
                    }
                    return "";
                } catch (JsonProcessingException e) {
                    System.err.println("Failed to parse chunk: " + chunk);
                    return "";
                }
            })
            .filter(chunk -> !chunk.isEmpty())
            .onErrorResume(e -> {
                System.err.println("Stream error: " + e.getMessage());
                return Flux.empty();
            });
    }

    private ObjectNode createMessage(String role, String content) {
        ObjectNode msg = objectMapper.createObjectNode();
        msg.put("role", role);
        msg.put("content", content);
        return msg;
    }

    @Override
    protected boolean supportsStreaming() {
        return true;
    }
}