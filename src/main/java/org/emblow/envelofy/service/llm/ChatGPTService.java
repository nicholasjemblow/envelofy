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

public class ChatGPTService extends AbstractLLMService {
    private final String openaiApiKey;
    private final String openaiChatEndpoint;
    private final WebClient webClient;

    public ChatGPTService(
            TransactionService transactionService,
            EnvelopeService envelopeService,
            SpendingInsightService insightService,
            PatternService patternService,
            AdvancedMLService advancedMLService,
            IntentDetectionService intentDetectionService,
            AccountService accountService,
            String openaiApiKey,
            String openaiChatEndpoint) {
        super(transactionService, envelopeService, insightService, patternService,
              advancedMLService, intentDetectionService, accountService);
        this.openaiApiKey = openaiApiKey;
        this.openaiChatEndpoint = openaiChatEndpoint;
        this.webClient = WebClient.builder()
            .baseUrl(openaiChatEndpoint)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + openaiApiKey)
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
            requestBody.put("model", "gpt-3.5-turbo");
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
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            JsonNode root = objectMapper.readTree(responseBody);
            if (root.has("choices") && root.get("choices").isArray() && root.get("choices").size() > 0) {
                return root.get("choices").get(0).get("message").get("content").asText();
            }
            return "No response received from ChatGPT.";
        } catch (Exception e) {
            throw new RuntimeException("Error during ChatGPT inference: " + e.getMessage(), e);
        }
    }

    @Override
    protected Flux<String> doStreamUserQuery(String userQuery) {
        String context = buildFinancialContext();
        String fullPrompt = buildPrompt(userQuery, context);

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", "gpt-3.5-turbo");
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
            .bodyValue(requestBody)
            .retrieve()
            .bodyToFlux(String.class)
            .filter(chunk -> chunk.startsWith("data: ") && !chunk.contains("[DONE]"))
            .map(chunk -> {
                try {
                    String json = chunk.substring(6).trim();
                    JsonNode node = objectMapper.readTree(json);
                    if (node.has("choices") && node.get("choices").size() > 0) {
                        JsonNode delta = node.get("choices").get(0).get("delta");
                        return delta.has("content") ? delta.get("content").asText() : "";
                    }
                    return "";
                } catch (Exception e) {
                    throw new RuntimeException("Error parsing ChatGPT stream chunk: " + e.getMessage(), e);
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