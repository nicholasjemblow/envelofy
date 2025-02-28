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
package org.emblow.envelofy.config;

import org.emblow.envelofy.service.AccountService;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.emblow.envelofy.service.SettingService;
import org.emblow.envelofy.service.TransactionService;
import org.emblow.envelofy.service.EnvelopeService;
import org.emblow.envelofy.service.ml.SpendingInsightService;
import org.emblow.envelofy.service.PatternService;
import org.emblow.envelofy.service.IntentDetectionService;
import org.emblow.envelofy.service.ml.AdvancedMLService;
import org.emblow.envelofy.service.llm.LLMService;
import org.emblow.envelofy.service.llm.ChatGPTService;
import org.emblow.envelofy.service.llm.JllamaService;
import org.emblow.envelofy.service.llm.OllamaService;
import org.emblow.envelofy.service.llm.GroqService;

@Configuration
public class LLMConfig {

    private final SettingService settingService;

    public LLMConfig(SettingService settingService) {
        this.settingService = settingService;
    }

    @Bean
    @Primary
    @RefreshScope
    public LLMService llmService(
            TransactionService transactionService,
            EnvelopeService envelopeService,
            SpendingInsightService insightService,
            PatternService patternService,
            AdvancedMLService advancedMLService,
            IntentDetectionService intentDetectionService,
            AccountService accountService
    ) {
        String type = settingService.getSetting("llm.service.type", "jllama").trim().toLowerCase();
        switch (type) {
            case "chatgpt":
                String openaiApiKey = settingService.getSetting("openai.api.key", "sk-default");
                String openaiChatEndpoint = settingService.getSetting("openai.chat.endpoint", "https://api.openai.com/v1/chat/completions");
                return new ChatGPTService(
                    transactionService, envelopeService, insightService, patternService, 
                    advancedMLService, intentDetectionService, accountService,
                    openaiApiKey, openaiChatEndpoint
                );
            case "jllama":
                String jllamaDirectory = settingService.getSetting("jllama.model.directory", "./models");
                String jllamaModelName = settingService.getSetting("jllama.model.name", "tjake/Llama-3.2-1B-Instruct-JQ4");
                return new JllamaService(
                    transactionService, envelopeService, insightService, patternService, 
                    advancedMLService, intentDetectionService, accountService,
                    jllamaModelName, jllamaDirectory
                );
            case "ollama":
                String ollamaUrl = settingService.getSetting("ollama.url", "http://localhost:11434");
                String ollamaModel = settingService.getSetting("ollama.model", "dolphin3:latest");
                return new OllamaService(
                    transactionService, envelopeService, insightService, patternService, 
                    advancedMLService, intentDetectionService, accountService,
                    ollamaUrl, ollamaModel
                );
            case "groq":
                String groqApiKey = settingService.getSetting("groq.api.key", "gsk-default");
                String groqBaseUrl = settingService.getSetting("groq.base.url", "https://api.groq.com");
                String groqModel = settingService.getSetting("groq.chat.model", "mixtral-8x7b-32768");
                return new GroqService(
                    transactionService, envelopeService, insightService, patternService, 
                    advancedMLService, intentDetectionService, accountService,
                    groqApiKey, groqBaseUrl, groqModel
                );
            default:
                throw new IllegalArgumentException("Unknown LLM service type: " + type);
        }
    }
}