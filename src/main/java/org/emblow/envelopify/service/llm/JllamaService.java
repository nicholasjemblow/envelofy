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
 *
 * @author Nicholas J Emblow
 */
import com.github.tjake.jlama.model.AbstractModel;
import com.github.tjake.jlama.model.ModelSupport;
import com.github.tjake.jlama.model.functions.Generator;
import com.github.tjake.jlama.safetensors.DType;
import com.github.tjake.jlama.safetensors.prompt.PromptContext;
import com.github.tjake.jlama.util.Downloader;
import org.emblow.envelopify.domain.Envelope;
import org.emblow.envelopify.domain.Transaction;
import org.emblow.envelopify.service.EnvelopeService;
import org.emblow.envelopify.service.PatternService;
import org.emblow.envelopify.service.TransactionService;
import org.emblow.envelopify.service.ml.SpendingInsight;
import org.emblow.envelopify.service.ml.SpendingInsightService;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class JllamaService implements LLMService {

    // Financial services used to build context remain unchanged.
    private final TransactionService transactionService;
    private final EnvelopeService envelopeService;
    private final SpendingInsightService insightService;
    private final PatternService patternService;

    // JLama model field – lazily loaded
    private AbstractModel model;
    
    @Autowired
    public JllamaService(
            TransactionService transactionService,
            EnvelopeService envelopeService,
            SpendingInsightService insightService,
            PatternService patternService
    ) {
        this.transactionService = transactionService;
        this.envelopeService = envelopeService;
        this.insightService = insightService;
        this.patternService = patternService;
    }

    /**
     * Processes the user query by building financial context, assembling a prompt, and then generating
     * a response using the JLama inference engine.
     * @param userQuery
     * @return 
     */
    @Override
    public String processUserQuery(String userQuery) {
        try {
            String context = buildFinancialContext();
            String fullPrompt = buildPrompt(userQuery, context);

            // Ensure the model is loaded
            AbstractModel localModel = getModel();

            // Build a prompt context. If the model supports chat prompts,
            // add a system message and the user prompt.
            PromptContext ctx;
            if (localModel.promptSupport().isPresent()) {
                ctx = localModel.promptSupport()
                        .get()
                        .builder()
                        .addSystemMessage("You are a financial analysis assistant with direct access to the user's financial data.")
                        .addUserMessage(fullPrompt)
                        .build();
            } else {
                ctx = PromptContext.of(fullPrompt);
            }

            // Generate a response using JLama
            Generator.Response response = localModel.generateBuilder()
                    .session(UUID.randomUUID())
                    .promptContext(ctx)
                    .ntokens(8096)
                    .temperature(0.2f)
                    .onTokenWithTimings((token, timing) -> {
                        // Optionally, process streaming tokens here.
                    })
                    .generate();

            return response.responseText;
        } catch (IOException | RestClientException e) {
            throw new RuntimeException("Error during JLama inference: " + e.getMessage(), e);
        }
    }

    /**
     * Lazily loads the JLama model. This method downloads the model (if needed) and loads it
     * using JLama's ModelSupport API.
     */
    private synchronized AbstractModel getModel() throws IOException {
        if (model == null) {
            // Define your model and working directory.
            String modelName = "tjake/Llama-3.2-1B-Instruct-JQ4";
            String workingDirectory = "./models";

            // Download the model (or get the local path if already downloaded)
            File localModelPath = new Downloader(workingDirectory, modelName).huggingFaceModel();

            // Load the model. Here we choose to load with F32 for working memory and I8 for quantized memory.
            model = ModelSupport.loadModel(localModelPath, DType.F32, DType.I8);
        }
        return model;
    }

    /**
     * Builds a financial context string that includes current envelope information,
     * recent transactions, financial insights, overall status, and spending patterns.
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

        // Merge regular and advanced insights.
        List<SpendingInsight> regularInsights = insightService.generateInsights();
        // (Optionally, you might merge in advanced insights from another source if needed.)
        List<SpendingInsight> allInsights = new ArrayList<>(regularInsights);
        context.append("\nFinancial Insights:\n");
        for (SpendingInsight insight : allInsights) {
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

        // Spending patterns from recent transactions.
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
     * Builds the prompt to be sent to the LLM by combining the financial context with the user's query.
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
}

