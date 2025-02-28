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

public class OllamaService extends AbstractLLMService {
    private final String ollamaUrl;
    private final String ollamaModel;
    private final WebClient webClient;

    public OllamaService(
            TransactionService transactionService,
            EnvelopeService envelopeService,
            SpendingInsightService insightService,
            PatternService patternService,
            AdvancedMLService advancedMLService,
            IntentDetectionService intentDetectionService,
            AccountService accountService,
            String ollamaUrl,
            String ollamaModel) {
        super(transactionService, envelopeService, insightService, patternService,
              advancedMLService, intentDetectionService, accountService);
        this.ollamaUrl = ollamaUrl;
        this.ollamaModel = ollamaModel;
        this.webClient = WebClient.builder()
            .baseUrl(ollamaUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    @Override
    public String processUserQuery(String userQuery) {
        // Delegate to AbstractLLMService, which handles context, prompt, and function calls
        return super.processUserQuery(userQuery);
    }

    @Override
    protected String callLLM(String prompt) {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", ollamaModel);
            requestBody.put("stream", false);
            requestBody.put("temperature", 0.0);
            requestBody.put("top_p", 1.0);
            requestBody.put("frequency_penalty", 0.0);
            requestBody.put("repeat_penalty", 1.0);
            requestBody.put("mirostat", 0);

            ArrayNode messages = objectMapper.createArrayNode();
            messages.add(createMessage("system", "You are a financial analysis assistant with direct access to the user's financial data."));
            messages.add(createMessage("user", prompt));
            requestBody.set("messages", messages);

            String responseBody = webClient.post()
                .uri("/api/chat")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            JsonNode root = objectMapper.readTree(responseBody);
            if (root.has("message") && root.get("message").has("content")) {
                return root.get("message").get("content").asText();
            }
            return "No response received from Ollama.";
        } catch (Exception e) {
            throw new RuntimeException("Error during Ollama inference: " + e.getMessage(), e);
        }
    }

    @Override
    protected Flux<String> doStreamUserQuery(String userQuery) {
        String context = buildFinancialContext();
        String fullPrompt = buildPrompt(userQuery, context);

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", ollamaModel);
        requestBody.put("stream", false);
        requestBody.put("temperature", 0.0);
        requestBody.put("top_p", 1.0);
        requestBody.put("frequency_penalty", 0.0);
        requestBody.put("repeat_penalty", 1.0);
        requestBody.put("mirostat", 0);

        ArrayNode messages = objectMapper.createArrayNode();
        messages.add(createMessage("system", "You are a financial analysis assistant with direct access to the user's financial data."));
        messages.add(createMessage("user", fullPrompt));
        requestBody.set("messages", messages);

        return webClient.post()
            .uri("/api/chat")
            .bodyValue(requestBody)
            .retrieve()
            .bodyToFlux(String.class)
            .map(chunk -> {
                try {
                    JsonNode node = objectMapper.readTree(chunk);
                    if (node.has("message") && node.get("message").has("content")) {
                        return node.get("message").get("content").asText();
                    }
                    return "";
                } catch (Exception e) {
                    throw new RuntimeException("Error parsing Ollama stream chunk: " + e.getMessage(), e);
                }
            })
            .filter(chunk -> !chunk.isEmpty());
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