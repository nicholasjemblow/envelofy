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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.emblow.envelofy.domain.Envelope;
import org.emblow.envelofy.domain.Transaction;
import org.emblow.envelofy.service.EnvelopeService;
import org.emblow.envelofy.service.PatternService;
import org.emblow.envelofy.service.TransactionService;
import org.emblow.envelofy.service.IntentDetectionService;
import org.emblow.envelofy.service.ml.AdvancedMLService;
import org.emblow.envelofy.service.ml.SpendingInsight;
import org.emblow.envelofy.service.ml.SpendingInsightService;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Flux;
import org.emblow.envelofy.domain.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import org.emblow.envelofy.domain.Account;
import org.emblow.envelofy.service.AccountService;
import org.emblow.envelofy.service.ml.SpendingInsightType;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public abstract class AbstractLLMService implements LLMService {

    // Common dependencies
    protected final TransactionService transactionService;
    protected final EnvelopeService envelopeService;
    protected final SpendingInsightService insightService;
    protected final PatternService patternService;
    protected final AdvancedMLService advancedMLService;
    protected final IntentDetectionService intentDetectionService;
    protected final AccountService accountService;
    
    // Common utilities
    protected final RestTemplate restTemplate;
    protected final ObjectMapper objectMapper;

    protected AbstractLLMService(
            TransactionService transactionService,
            EnvelopeService envelopeService,
            SpendingInsightService insightService,
            PatternService patternService,
            AdvancedMLService advancedMLService,
            IntentDetectionService intentDetectionService,
            AccountService accountService
    ) {
        this.transactionService = transactionService;
        this.envelopeService = envelopeService;
        this.insightService = insightService;
        this.patternService = patternService;
        this.advancedMLService = advancedMLService;
        this.intentDetectionService = intentDetectionService;
        this.accountService = accountService;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String processUserQuery(String userQuery) {
        String context = buildFinancialContext();
        String prompt = buildPrompt(userQuery, context);
        String rawResponse = callLLM(prompt);
        return processFunctionCalls(rawResponse);
    }
/************* getting issues with streaming processing function calls
    @Override
    public Flux<String> streamUserQuery(String userQuery) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (supportsStreaming()) {
            return doStreamUserQuery(userQuery)
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
        }
        return Flux.just(processUserQuery(userQuery))
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
    }
***********/
    @Override
    public Flux<String> streamUserQuery(String userQuery) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // Just return the complete response as a single element flux
        return Flux.just(processUserQuery(userQuery))
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
    }
    protected abstract boolean supportsStreaming();

    protected Flux<String> doStreamUserQuery(String userQuery) {
        throw new UnsupportedOperationException("Streaming not supported by this implementation");
    }

    protected String buildFinancialContext() {
        Map<String, Object> realTimeData = getRealTimeFinancialData();
        StringBuilder context = new StringBuilder();

        // Add real-time account balances
        context.append("Current Account Balances:\n");
        ((List<Map<String, Object>>)realTimeData.get("accounts"))
            .forEach(acc -> context.append(String.format("- %s (%s): $%.2f\n",
                acc.get("name"),
                acc.get("type"),
                acc.get("balance"))));

        // Add real-time envelope statuses
        context.append("\nEnvelope Statuses:\n");
        ((List<Map<String, Object>>)realTimeData.get("envelopes"))
            .forEach(env -> context.append(String.format("- %s: Available $%.2f, Spent $%.2f, Allocated $%.2f\n",
                env.get("name"),
                env.get("available"),
                env.get("spent"),
                env.get("allocated"))));
        
        // Recent transactions
        LocalDateTime oneMonthAgo = LocalDateTime.now().minusMonths(1);
        List<Transaction> recentTransactions = transactionService.getRecentTransactions(
                oneMonthAgo,
                LocalDateTime.now()
        );
        
        context.append("\nRecent Transactions:\n");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        for (Transaction tx : recentTransactions.stream().limit(10).collect(Collectors.toList())) {
            context.append(String.format("- %s: $%.2f in %s (%s) from %s\n",
                    tx.getDate().format(formatter),
                    tx.getAmount(),
                    tx.getEnvelope().getName(),
                    tx.getDescription(),
                    tx.getAccount().getName()));
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

        // Overall financial status
        BigDecimal totalBudget = envelopeService.getAllEnvelopes().stream()
                .map(Envelope::getMonthlyBudget)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
                
        BigDecimal totalAvailable = envelopeService.getAllEnvelopes().stream()
                .map(Envelope::getAvailable)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
                
        context.append(String.format("\nOverall Status:\n- Total Budget: $%.2f\n- Total Available: $%.2f\n",
                totalBudget, totalAvailable));

        context.append("\nSpending By Category (Last 30 Days):\n");
        Map<String, BigDecimal> categorySpending = 
            (Map<String, BigDecimal>) realTimeData.get("categorySpending");
        categorySpending.forEach((category, amount) ->
            context.append(String.format("- %s: $%.2f\n", category, amount)));
        
        return context.toString();
    }

    protected String buildPrompt(String userQuery, String context) {
        return String.format("""
            Role: You are a financial analysis assistant specializing in personal finance and budgeting.
            
            Available Financial Data:
            %s
            
            Available Functions:
            - get_envelope_balance(envelope_name): Returns the current balance of the specified envelope.
            - generate_spending_chart(start_date, end_date, chart_type): Generates a chart of spending (e.g., 'pie', 'bar').
            - generate_transaction_table(start_date, end_date, limit): Generates a table of recent transactions.
            - get_total_spent(envelope_name, start_date, end_date): Returns total spending in an envelope over a period.
            
            User Question: %s
            
            Instructions:
            1. Analyze the financial data thoroughly.
            2. Use function calls for precise data retrieval or visualization (e.g., charts, tables).
            3. Return response in this JSON format:
            {
              "text": "Your analysis in markdown format",
              "charts": [{"function": "function_name", "params": {"param1": "value1", ...}} or chart_data],
              "tables": [{"function": "function_name", "params": {"param1": "value1", ...}} or table_data]
            }
            """, context, userQuery);
    }

    protected String processFunctionCalls(String rawResponse) {
        try {
            Map<String, Object> response = objectMapper.readValue(rawResponse, Map.class);
            List<Map<String, Object>> charts = (List<Map<String, Object>>) response.getOrDefault("charts", new ArrayList<>());
            List<Map<String, Object>> tables = (List<Map<String, Object>>) response.getOrDefault("tables", new ArrayList<>());

            // Process charts
            for (Map<String, Object> chart : charts) {
                if (chart.containsKey("function")) {
                    String functionName = (String) chart.get("function");
                    Map<String, Object> params = (Map<String, Object>) chart.get("params");
                    Map<String, Object> chartData = executeFunction(functionName, params);
                    chart.clear();
                    chart.putAll(chartData);
                }
            }

            // Process tables
            for (Map<String, Object> table : tables) {
                if (table.containsKey("function")) {
                    String functionName = (String) table.get("function");
                    Map<String, Object> params = (Map<String, Object>) table.get("params");
                    Map<String, Object> tableData = executeFunction(functionName, params);
                    table.clear();
                    table.putAll(tableData);
                }
            }

            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            return "{\"text\": \"Error processing response: " + e.getMessage() + "\", \"charts\": [], \"tables\": []}";
        }
    }

    protected Map<String, Object> executeFunction(String functionName, Map<String, Object> params) {
        switch (functionName) {
            case "generate_spending_chart": {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
                LocalDate startDate = LocalDate.parse((String) params.get("start_date"), formatter);
                LocalDate endDate = LocalDate.parse((String) params.get("end_date"), formatter);
                LocalDateTime start = startDate.atStartOfDay();
                LocalDateTime end = endDate.atTime(23, 59, 59);
                String chartType = (String) params.get("chart_type");
                return buildSpendingChart(start, end, chartType);
            }
            case "generate_transaction_table": {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
                LocalDate startDate = LocalDate.parse((String) params.get("start_date"), formatter);
                LocalDate endDate = LocalDate.parse((String) params.get("end_date"), formatter);
                LocalDateTime start = startDate.atStartOfDay();
                LocalDateTime end = endDate.atTime(23, 59, 59);
                int limit = params.containsKey("limit") ? ((Number) params.get("limit")).intValue() : 10;
                return buildTransactionTable(start, end, limit);
            }
            case "get_envelope_balance": {
                String envelopeName = (String) params.get("envelope_name");
                return Map.of("text", "$" + calculateAvailableInEnvelope(envelopeName).toString());
            }
            case "get_total_spent": {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
                LocalDate startDate = LocalDate.parse((String) params.get("start_date"), formatter);
                LocalDate endDate = LocalDate.parse((String) params.get("end_date"), formatter);
                LocalDateTime start = startDate.atStartOfDay();
                LocalDateTime end = endDate.atTime(23, 59, 59);
                String envelopeName = (String) params.get("envelope_name");
                BigDecimal totalSpent = calculateTotalSpent(envelopeName, start, end);
                return Map.of("text", String.format("Total spent in %s from %s to %s: $%s",
                    envelopeName, params.get("start_date"), params.get("end_date"), totalSpent.toString()));
            }
            default:
                throw new IllegalArgumentException("Unknown function: " + functionName);
        }
    }

    protected Map<String, Object> buildSpendingChart(LocalDateTime start, LocalDateTime end, String chartType) {
        Map<String, BigDecimal> spending = getSpendingByCategory(start, end);
        List<String> labels = new ArrayList<>(spending.keySet());
        List<BigDecimal> data = new ArrayList<>(spending.values());

        Map<String, Object> chartData = new HashMap<>();
        chartData.put("type", chartType);

        Map<String, Object> dataset = new HashMap<>();
        dataset.put("label", "Spending");
        dataset.put("data", data);
        dataset.put("backgroundColor", "rgba(75, 192, 192, 0.2)");
        dataset.put("borderColor", "rgba(75, 192, 192, 1)");
        dataset.put("borderWidth", 1);

        Map<String, Object> chartDataset = new HashMap<>();
        chartDataset.put("labels", labels);
        chartDataset.put("datasets", List.of(dataset));

        chartData.put("data", chartDataset);
        chartData.put("options", Map.of("scales", Map.of("y", Map.of("beginAtZero", true))));

        return chartData;
    }

    protected Map<String, Object> buildTransactionTable(LocalDateTime start, LocalDateTime end, int limit) {
        List<Transaction> transactions = transactionService.getRecentTransactions(start, end)
            .stream()
            .limit(limit)
            .collect(Collectors.toList());

        List<String> headers = List.of("Date", "Amount", "Envelope", "Description", "Account");

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        List<List<String>> rows = transactions.stream()
            .map(tx -> List.of(
                tx.getDate().format(dateFormatter),
                "$" + tx.getAmount().toString(),
                tx.getEnvelope().getName(),
                tx.getDescription(),
                tx.getAccount().getName()
            ))
            .collect(Collectors.toList());

        return Map.of(
            "headers", headers,
            "rows", rows
        );
    }

    protected BigDecimal calculateAvailableInEnvelope(String envelopeName) {
        return envelopeService.getAllEnvelopes().stream()
            .filter(env -> env.getName().equalsIgnoreCase(envelopeName))
            .findFirst()
            .map(Envelope::getAvailable)
            .orElse(BigDecimal.ZERO);
    }

    protected BigDecimal calculateTotalSpent(String envelopeName, LocalDateTime start, LocalDateTime end) {
        Envelope envelope = envelopeService.getAllEnvelopes().stream()
            .filter(env -> env.getName().equalsIgnoreCase(envelopeName))
            .findFirst()
            .orElse(null);

        if (envelope == null) {
            return BigDecimal.ZERO;
        }

        return transactionService.getRecentTransactions(start, end).stream()
            .filter(tx -> tx.getEnvelope().getId().equals(envelope.getId()))
            .filter(tx -> tx.getType() == TransactionType.EXPENSE)
            .map(Transaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    protected Map<String, BigDecimal> getSpendingByCategory(LocalDateTime start, LocalDateTime end) {
        return transactionService.getRecentTransactions(start, end)
            .stream()
            .filter(tx -> tx.getType() == TransactionType.EXPENSE)
            .collect(Collectors.groupingBy(
                tx -> tx.getEnvelope().getName(),
                Collectors.mapping(
                    Transaction::getAmount,
                    Collectors.reducing(BigDecimal.ZERO, BigDecimal::add)
                )
            ));
    }

    protected List<SpendingInsight> getAdvancedMLInsights() {
        List<AdvancedMLService.AccountAnalysis> analyses = advancedMLService.analyzeAccounts();
        List<SpendingInsight> mlInsights = new ArrayList<>();
        
        for (AdvancedMLService.AccountAnalysis analysis : analyses) {
            for (AdvancedMLService.AnomalyDetection anomaly : analysis.getAnomalies()) {
                if (anomaly.type() == AdvancedMLService.AnomalyType.AMOUNT) {
                    mlInsights.add(new SpendingInsight(
                            SpendingInsightType.UNUSUAL_SPENDING,
                            anomaly.description(),
                            anomaly.severity()
                    ));
                } else if (anomaly.type() == AdvancedMLService.AnomalyType.FREQUENCY) {
                    mlInsights.add(new SpendingInsight(
                            SpendingInsightType.RECURRING_PAYMENT,
                            anomaly.description(),
                            anomaly.severity()
                    ));
                }
            }

            analysis.getMerchantMetrics().forEach((merchant, metrics) -> {
                if (metrics.monthlyFrequency() >= 1.0) {
                    mlInsights.add(new SpendingInsight(
                            SpendingInsightType.RECURRING_PAYMENT,
                            String.format("Recurring payment detected at %s (monthly frequency: %.1f)",
                                merchant, metrics.monthlyFrequency()),
                            Math.min(1.0, metrics.monthlyFrequency() / 5.0)
                    ));
                }
            });

            analysis.getEnvelopeMetrics().forEach((envelope, envMetrics) -> {
                if (envMetrics.budgetUtilization() > 0.9) {
                    mlInsights.add(new SpendingInsight(
                            SpendingInsightType.BUDGET_SUGGESTION,
                            String.format("Envelope %s is at %.0f%% of budget. Consider adjustment.",
                                    envelope.getName(),
                                    envMetrics.budgetUtilization() * 100),
                            envMetrics.budgetUtilization()
                    ));
                }
            });

            if (analysis.getVolumeTrend() > 0.2) {
                mlInsights.add(new SpendingInsight(
                        SpendingInsightType.PREDICTED_EXPENSE,
                        String.format("Spending trending upward for %s account. Review budget recommended.",
                                analysis.getAccount().getName()),
                        0.8
                ));
            } else if (analysis.getVolumeTrend() < -0.2) {
                mlInsights.add(new SpendingInsight(
                        SpendingInsightType.SEASONAL_PATTERN,
                        String.format("Seasonal spending decrease detected in %s account.",
                                analysis.getAccount().getName()),
                        0.7
                ));
            }
        }
        return mlInsights;
    }
    
    protected Map<String, Object> getRealTimeFinancialData() {
        Map<String, Object> data = new HashMap<>();

        List<Envelope> envelopes = envelopeService.getAllEnvelopes();
        data.put("envelopes", envelopes.stream()
            .map(env -> Map.of(
                "name", env.getName(),
                "available", env.getAvailable(),
                "allocated", env.getAllocated(),
                "spent", env.getSpent(),
                "monthlyBudget", env.getMonthlyBudget() != null ? env.getMonthlyBudget() : BigDecimal.ZERO
            ))
            .collect(Collectors.toList()));

        List<Account> accounts = accountService.getAllAccounts();
        data.put("accounts", accounts.stream()
            .map(acc -> Map.of(
                "name", acc.getName(),
                "type", acc.getType().getDisplayName(),
                "balance", acc.getBalance(),
                "institution", acc.getInstitution() != null ? acc.getInstitution() : "N/A"
            ))
            .collect(Collectors.toList()));

        LocalDateTime oneMonthAgo = LocalDateTime.now().minusMonths(1);
        Map<String, BigDecimal> categorySpending = getSpendingByCategory(oneMonthAgo, LocalDateTime.now());
        data.put("categorySpending", categorySpending);

        List<Transaction> recentTransactions = transactionService.getRecentTransactions(
            oneMonthAgo, LocalDateTime.now()
        );
        data.put("recentTransactions", recentTransactions.stream()
            .limit(10)
            .map(tx -> Map.of(
                "date", tx.getDate().format(DateTimeFormatter.ofPattern("MM/dd/yyyy")),
                "amount", tx.getAmount(),
                "envelope", tx.getEnvelope().getName(),
                "description", tx.getDescription(),
                "account", tx.getAccount().getName(),
                "type", tx.getType().toString()
            ))
            .collect(Collectors.toList()));

        BigDecimal totalAvailable = envelopes.stream()
            .map(Envelope::getAvailable)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalBalance = accounts.stream()
            .map(Account::getBalance)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        data.put("totalAvailable", totalAvailable);
        data.put("totalBalance", totalBalance);

        return data;
    }

    protected abstract String callLLM(String prompt);
}