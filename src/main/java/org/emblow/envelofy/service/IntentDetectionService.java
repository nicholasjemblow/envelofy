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

/**
 *
 * @author Nicholas J Emblow
 */
import org.emblow.envelofy.service.ml.TransactionNaiveBayes;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class IntentDetectionService {
    private final TransactionNaiveBayes classifier;

    public IntentDetectionService() {
        this.classifier = new TransactionNaiveBayes();
        trainClassifier();
    }

    private void trainClassifier() {
        List<TransactionNaiveBayes.TransactionFeatures> trainingData = new ArrayList<>();

        // Budget-related queries (50 samples)
        trainingData.add(createFeatures("How much is my budget?", "budget"));
        trainingData.add(createFeatures("What's my remaining budget this month?", "budget"));
        trainingData.add(createFeatures("Can I increase my groceries budget?", "budget"));
        trainingData.add(createFeatures("Is my budget sufficient for this month?", "budget"));
        trainingData.add(createFeatures("Do I have enough budget left for dining out?", "budget"));
        trainingData.add(createFeatures("What's my monthly budget cap?", "budget"));
        trainingData.add(createFeatures("Can you tell me my current budget?", "budget"));
        trainingData.add(createFeatures("How much budget have I allocated for rent?", "budget"));
        trainingData.add(createFeatures("Budget status update", "budget"));
        trainingData.add(createFeatures("What is my budget limit?", "budget"));
        trainingData.add(createFeatures("Show my budget allocation", "budget"));
        trainingData.add(createFeatures("Budget details for this month", "budget"));
        trainingData.add(createFeatures("Tell me my budget overview", "budget"));
        trainingData.add(createFeatures("What is my total budget?", "budget"));
        trainingData.add(createFeatures("Give me a budget summary", "budget"));
        trainingData.add(createFeatures("How much money is allocated in my budget?", "budget"));
        trainingData.add(createFeatures("Check my budget balance", "budget"));
        trainingData.add(createFeatures("What's left in my budget?", "budget"));
        trainingData.add(createFeatures("Budget insights", "budget"));
        trainingData.add(createFeatures("Am I exceeding my budget?", "budget"));
        trainingData.add(createFeatures("Update on my budget", "budget"));
        trainingData.add(createFeatures("Remaining funds in my budget", "budget"));
        trainingData.add(createFeatures("Monthly budget report", "budget"));
        trainingData.add(createFeatures("Budget utilization status", "budget"));
        trainingData.add(createFeatures("How do I compare to my budget?", "budget"));
        trainingData.add(createFeatures("Is my budget balanced?", "budget"));
        trainingData.add(createFeatures("Budget check", "budget"));
        trainingData.add(createFeatures("Detail my budget", "budget"));
        trainingData.add(createFeatures("Budget performance", "budget"));
        trainingData.add(createFeatures("Track my budget usage", "budget"));
        trainingData.add(createFeatures("What's my entertainment budget?", "budget"));
        trainingData.add(createFeatures("Show budget categories", "budget"));
        trainingData.add(createFeatures("Budget breakdown by category", "budget"));
        trainingData.add(createFeatures("How much can I spend on utilities?", "budget"));
        trainingData.add(createFeatures("What's my discretionary budget?", "budget"));
        trainingData.add(createFeatures("Budget limits for each category", "budget"));
        trainingData.add(createFeatures("Am I within budget this quarter?", "budget"));
        trainingData.add(createFeatures("Show me my savings budget", "budget"));
        trainingData.add(createFeatures("What's my holiday spending budget?", "budget"));
        trainingData.add(createFeatures("Emergency fund budget status", "budget"));
        trainingData.add(createFeatures("Budget allocation by percentage", "budget"));
        trainingData.add(createFeatures("How much is budgeted for car expenses?", "budget"));
        trainingData.add(createFeatures("What's my annual budget plan?", "budget"));
        trainingData.add(createFeatures("Show me my flexible spending budget", "budget"));
        trainingData.add(createFeatures("Budget variance analysis", "budget"));
        trainingData.add(createFeatures("What's my travel budget?", "budget"));
        trainingData.add(createFeatures("Healthcare budget status", "budget"));
        trainingData.add(createFeatures("Education budget overview", "budget"));
        trainingData.add(createFeatures("Budget vs actual spending", "budget"));
        trainingData.add(createFeatures("Show my clothing budget", "budget"));

        // Spending-related queries (50 samples)
        trainingData.add(createFeatures("How much did I spend this month?", "spending"));
        trainingData.add(createFeatures("What did I spend on entertainment?", "spending"));
        trainingData.add(createFeatures("Show me my recent spending", "spending"));
        trainingData.add(createFeatures("What are my expenses this month?", "spending"));
        trainingData.add(createFeatures("List my spending for the last week", "spending"));
        trainingData.add(createFeatures("Track my spending habits", "spending"));
        trainingData.add(createFeatures("How much did I spend on groceries?", "spending"));
        trainingData.add(createFeatures("Spending details for this month", "spending"));
        trainingData.add(createFeatures("What did I spend on dining out?", "spending"));
        trainingData.add(createFeatures("Can you show my spending history?", "spending"));
        trainingData.add(createFeatures("Provide a spending summary", "spending"));
        trainingData.add(createFeatures("What's my total expenditure this month?", "spending"));
        trainingData.add(createFeatures("Detail my spending on utilities", "spending"));
        trainingData.add(createFeatures("Recent spending breakdown", "spending"));
        trainingData.add(createFeatures("Track my expenditure", "spending"));
        trainingData.add(createFeatures("How much money have I spent?", "spending"));
        trainingData.add(createFeatures("Spending overview", "spending"));
        trainingData.add(createFeatures("Expense details for this week", "spending"));
        trainingData.add(createFeatures("Can I see my spending chart?", "spending"));
        trainingData.add(createFeatures("What are my major expenses?", "spending"));
        trainingData.add(createFeatures("Provide a breakdown of my spending", "spending"));
        trainingData.add(createFeatures("Track my daily spending", "spending"));
        trainingData.add(createFeatures("What did I spend on transportation?", "spending"));
        trainingData.add(createFeatures("Spending report", "spending"));
        trainingData.add(createFeatures("How are my spending patterns?", "spending"));
        trainingData.add(createFeatures("Show my expense report", "spending"));
        trainingData.add(createFeatures("Monthly spending details", "spending"));
        trainingData.add(createFeatures("How much did I overspend this month?", "spending"));
        trainingData.add(createFeatures("Spending data summary", "spending"));
        trainingData.add(createFeatures("Financial outflow details", "spending"));
        trainingData.add(createFeatures("Where did I spend the most money?", "spending"));
        trainingData.add(createFeatures("Show my recurring expenses", "spending"));
        trainingData.add(createFeatures("What did I spend at restaurants?", "spending"));
        trainingData.add(createFeatures("How much went to online shopping?", "spending"));
        trainingData.add(createFeatures("Show my subscription expenses", "spending"));
        trainingData.add(createFeatures("What did I spend on Amazon?", "spending"));
        trainingData.add(createFeatures("How much did I spend on coffee?", "spending"));
        trainingData.add(createFeatures("Show my weekend spending", "spending"));
        trainingData.add(createFeatures("What are my work-related expenses?", "spending"));
        trainingData.add(createFeatures("How much did I spend on gifts?", "spending"));
        trainingData.add(createFeatures("Show my medical expenses", "spending"));
        trainingData.add(createFeatures("What did I spend on home maintenance?", "spending"));
        trainingData.add(createFeatures("Detail my insurance payments", "spending"));
        trainingData.add(createFeatures("Show my pet expenses", "spending"));
        trainingData.add(createFeatures("How much did I spend on hobbies?", "spending"));
        trainingData.add(createFeatures("What are my cash withdrawals?", "spending"));
        trainingData.add(createFeatures("Show my charitable donations", "spending"));
        trainingData.add(createFeatures("Detail my gym and fitness expenses", "spending"));
        trainingData.add(createFeatures("What did I spend on books?", "spending"));
        trainingData.add(createFeatures("Show my technology purchases", "spending"));

        // Prediction-related queries (50 samples)
        trainingData.add(createFeatures("What will I spend next month?", "prediction"));
        trainingData.add(createFeatures("Predict my expenses for December", "prediction"));
        trainingData.add(createFeatures("How much will groceries cost next week?", "prediction"));
        trainingData.add(createFeatures("Forecast my expenses for the coming month", "prediction"));
        trainingData.add(createFeatures("Can you predict my spending for next month?", "prediction"));
        trainingData.add(createFeatures("What's the predicted expenditure for this month?", "prediction"));
        trainingData.add(createFeatures("Future spending prediction", "prediction"));
        trainingData.add(createFeatures("Estimate my upcoming expenses", "prediction"));
        trainingData.add(createFeatures("What are my projected costs?", "prediction"));
        trainingData.add(createFeatures("Predict future spending habits", "prediction"));
        trainingData.add(createFeatures("Forecast my monthly spending", "prediction"));
        trainingData.add(createFeatures("Can you estimate next month's expenses?", "prediction"));
        trainingData.add(createFeatures("What will be my expenditure trend?", "prediction"));
        trainingData.add(createFeatures("Predict expense forecast", "prediction"));
        trainingData.add(createFeatures("Anticipate my spending for next month", "prediction"));
        trainingData.add(createFeatures("Estimate my future financial outflow", "prediction"));
        trainingData.add(createFeatures("Future expense estimate", "prediction"));
        trainingData.add(createFeatures("Projected spending for next month", "prediction"));
        trainingData.add(createFeatures("How much am I expected to spend?", "prediction"));
        trainingData.add(createFeatures("Upcoming spending prediction", "prediction"));
        trainingData.add(createFeatures("What will my expenses look like next month?", "prediction"));
        trainingData.add(createFeatures("Forecast my future costs", "prediction"));
        trainingData.add(createFeatures("Predict my next month's spending", "prediction"));
        trainingData.add(createFeatures("Future budget forecast", "prediction"));
        trainingData.add(createFeatures("What expenses do I anticipate?", "prediction"));
        trainingData.add(createFeatures("Predict upcoming expenditures", "prediction"));
        trainingData.add(createFeatures("Estimate the cost for next week", "prediction"));
        trainingData.add(createFeatures("What is the forecast for my expenses?", "prediction"));
        trainingData.add(createFeatures("Anticipated spending details", "prediction"));
        trainingData.add(createFeatures("Estimate future outflow", "prediction"));
        trainingData.add(createFeatures("Will I stay within budget next month?", "prediction"));
        trainingData.add(createFeatures("Predict my holiday spending", "prediction"));
        trainingData.add(createFeatures("What will utilities cost next month?", "prediction"));
        trainingData.add(createFeatures("Forecast my subscription costs", "prediction"));
        trainingData.add(createFeatures("Predict next quarter's expenses", "prediction"));
        trainingData.add(createFeatures("How much should I save next month?", "prediction"));
        trainingData.add(createFeatures("What will my rent increase to?", "prediction"));
        trainingData.add(createFeatures("Predict my travel expenses", "prediction"));
        trainingData.add(createFeatures("Future entertainment costs", "prediction"));
        trainingData.add(createFeatures("Will I need to adjust my budget?", "prediction"));
        trainingData.add(createFeatures("Predict my annual expenses", "prediction"));
        trainingData.add(createFeatures("What will groceries cost next month?", "prediction"));
        trainingData.add(createFeatures("Forecast my insurance premiums", "prediction"));
        trainingData.add(createFeatures("Predict my car maintenance costs", "prediction"));
        trainingData.add(createFeatures("What will healthcare cost me?", "prediction"));
        trainingData.add(createFeatures("Estimate next year's expenses", "prediction"));
        trainingData.add(createFeatures("Predict my debt payments", "prediction"));
        trainingData.add(createFeatures("Future household expenses", "prediction"));
        trainingData.add(createFeatures("What will my tax burden be?", "prediction"));
        trainingData.add(createFeatures("Predict educational expenses", "prediction"));

        // General financial queries (50 samples)
        trainingData.add(createFeatures("Tell me about my finances", "general"));
        trainingData.add(createFeatures("What's my financial status?", "general"));
        trainingData.add(createFeatures("Help me with my money", "general"));
        trainingData.add(createFeatures("Show me my overall financial overview", "general"));
        trainingData.add(createFeatures("Give me a financial summary", "general"));
        trainingData.add(createFeatures("What's the state of my finances?", "general"));
        trainingData.add(createFeatures("Provide my financial report", "general"));
        trainingData.add(createFeatures("How healthy are my finances?", "general"));
        trainingData.add(createFeatures("Overall financial update", "general"));
        trainingData.add(createFeatures("Detail my financial situation", "general"));
        trainingData.add(createFeatures("What is my net worth?", "general"));
        trainingData.add(createFeatures("Show my financial breakdown", "general"));
        trainingData.add(createFeatures("Provide an overview of my money", "general"));
        trainingData.add(createFeatures("What does my financial picture look like?", "general"));
        trainingData.add(createFeatures("General financial health check", "general"));
        trainingData.add(createFeatures("How am I doing financially?", "general"));
        trainingData.add(createFeatures("Can you give me a financial overview?", "general"));
        trainingData.add(createFeatures("Current financial summary", "general"));
        trainingData.add(createFeatures("What are my financial metrics?", "general"));
        trainingData.add(createFeatures("Financial analysis of my expenses", "general"));
        trainingData.add(createFeatures("My financial snapshot", "general"));
        trainingData.add(createFeatures("Overall money management status", "general"));
        trainingData.add(createFeatures("Detailed financial status report", "general"));
        trainingData.add(createFeatures("Show my income and expenses overview", "general"));
        trainingData.add(createFeatures("Provide my fiscal overview", "general"));
        trainingData.add(createFeatures("What is my current monetary status?", "general"));
        trainingData.add(createFeatures("Update on my overall finances", "general"));
        trainingData.add(createFeatures("Comprehensive financial summary", "general"));
        trainingData.add(createFeatures("Financial condition update", "general"));
        trainingData.add(createFeatures("Detailed overview of my finances", "general"));
        trainingData.add(createFeatures("How's my cash flow looking?", "general"));
        trainingData.add(createFeatures("What's my financial health score?", "general"));
        trainingData.add(createFeatures("Show my asset allocation", "general"));
        trainingData.add(createFeatures("What are my top financial priorities?", "general"));
        trainingData.add(createFeatures("Give me a money management review", "general"));
        trainingData.add(createFeatures("How diversified are my investments?", "general"));
        trainingData.add(createFeatures("What's my debt-to-income ratio?", "general"));
        trainingData.add(createFeatures("Show my emergency fund status", "general"));
        trainingData.add(createFeatures("What's my savings rate?", "general"));
        trainingData.add(createFeatures("How much do I have in investments?", "general"));
        trainingData.add(createFeatures("What's my credit utilization?", "general"));
        trainingData.add(createFeatures("Show my retirement accounts", "general"));
        trainingData.add(createFeatures("What's my overall wealth trend?", "general"));
        trainingData.add(createFeatures("How much equity do I have?", "general"));
        trainingData.add(createFeatures("What's my monthly cash flow?", "general"));
        trainingData.add(createFeatures("Show my investment returns", "general"));
        trainingData.add(createFeatures("What's my financial risk profile?", "general"));
        trainingData.add(createFeatures("How much am I worth?", "general"));
        trainingData.add(createFeatures("Show my tax efficiency", "general"));
        trainingData.add(createFeatures("What's my liquidity position?", "general"));

        classifier.train(trainingData);
    }

    private TransactionNaiveBayes.TransactionFeatures createFeatures(String query, String intent) {
        return new TransactionNaiveBayes.TransactionFeatures(
            query,                // Description
            BigDecimal.ZERO,      // Amount (not used here)
            LocalDateTime.now(),  // Date (not used here)
            intent,               // Category (intent in this case)
            null,                 // AccountType (not used)
            ""                    // AccountName (not used)
        );
    }

    public String detectIntent(String query) {
        TransactionNaiveBayes.TransactionFeatures features = createFeatures(query, "unknown");
        Map<String, Double> predictions = classifier.predict(features);

        return predictions.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("general");
    }
}