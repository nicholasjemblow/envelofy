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
package org.emblow.envelopify.config;

/**
 *
 * @author Nicholas J Emblow
 */
import org.emblow.envelopify.service.llm.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.emblow.envelopify.service.*;
import org.emblow.envelopify.service.ml.AdvancedMLService;
import org.emblow.envelopify.service.ml.SpendingInsightService;

@Configuration
public class LLMConfig {
    
    @Value("${llm.service.type:jllama}")  // Default to jllama if not specified
    private String llmServiceType;
    
    @Bean
    @Primary
    public LLMService llmService(
        TransactionService transactionService,
        EnvelopeService envelopeService,
        SpendingInsightService insightService,
        PatternService patternService,
        AdvancedMLService advancedMLService
    ) {
        return switch (llmServiceType.toLowerCase().trim()) {
            
            case "chatgpt" -> new ChatGPTService(
                transactionService, 
                envelopeService, 
                insightService, 
                patternService
            );
            case "jllama" -> new JllamaService(
                transactionService, 
                envelopeService, 
                insightService, 
                patternService
            );
            case "ollama" -> new OllamaService(
                transactionService, 
                envelopeService, 
                insightService, 
                patternService,
                advancedMLService
            );
            default -> throw new IllegalArgumentException(
                "Unknown LLM service type: " + llmServiceType
            );
        };
    }
}