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
package org.emblow.envelofy.ui.views;

/**
 *
 * @author Nicholas J Emblow
 */
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.emblow.envelofy.ui.MainLayout;
import org.emblow.envelofy.ui.components.InsightsPanel;
import org.emblow.envelofy.service.ml.SpendingInsightService;
import org.emblow.envelofy.service.ml.SpendingInsight;

import java.util.List;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import java.util.ArrayList;
import java.util.stream.Collectors;
import org.emblow.envelofy.service.TransactionService;
import org.emblow.envelofy.service.ml.AdvancedMLService;
import org.emblow.envelofy.service.ml.SpendingInsightType;
import org.emblow.envelofy.ui.components.SpendingTrendsChart;

@Route(value = "insights", layout = MainLayout.class)
@PageTitle("Smart Insights | Envelofy")
@AnonymousAllowed
public class InsightsView extends VerticalLayout {
    
    private final SpendingInsightService insightService;
    private final TransactionService transactionService;
    private final InsightsPanel regularInsights;
    private final InsightsPanel predictiveInsights;
    private final SpendingTrendsChart trendsChart; 
    private final AdvancedMLService mlService;
    
     public InsightsView(SpendingInsightService insightService, 
                       TransactionService transactionService,
                       AdvancedMLService mlService) { // Add to constructor
        this.mlService = mlService;
        this.insightService = insightService;
        this.transactionService = transactionService;
        
        addClassName("insights-view");
        setSizeFull();
        setSpacing(true);
        setPadding(true);

        // Header with refresh button
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);
        header.setAlignItems(Alignment.CENTER);

        H2 title = new H2("Smart Insights");
        Button refreshButton = new Button(
            "Refresh Insights", 
            new Icon(VaadinIcon.REFRESH),
            e -> refreshInsights()
        );
        refreshButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        header.add(title, refreshButton);

        // Create spending trends chart card
        Div chartCard = new Div();
        chartCard.addClassName("chart-card");
        chartCard.getStyle()
            .set("background-color", "var(--lumo-base-color)")
            .set("border-radius", "var(--lumo-border-radius-l)")
            .set("box-shadow", "var(--lumo-box-shadow-s)")
            .set("padding", "var(--lumo-space-m)")
            .set("margin-bottom", "var(--lumo-space-m)");

        H2 chartTitle = new H2("Monthly Spending Trends");
        chartTitle.getStyle()
            .set("margin-top", "0")
            .set("margin-bottom", "var(--lumo-space-m)");

        trendsChart = new SpendingTrendsChart(transactionService);

        chartCard.add(chartTitle, trendsChart);
  
        // Create layout for insights panels
        HorizontalLayout content = new HorizontalLayout();
        content.setWidthFull();
        content.setSpacing(true);
        
        // Create two columns for different types of insights
        VerticalLayout leftColumn = new VerticalLayout();
        leftColumn.setPadding(false);
        leftColumn.setSpacing(true);
        leftColumn.setWidth("50%");
        leftColumn.add(new H2("Current Insights"));
        
        VerticalLayout rightColumn = new VerticalLayout();
        rightColumn.setPadding(false);
        rightColumn.setSpacing(true);
        rightColumn.setWidth("50%");
        rightColumn.add(new H2("Predictive Insights"));
        
        // Initialize insights panels
        regularInsights = new InsightsPanel();
        predictiveInsights = new InsightsPanel();
        
        leftColumn.add(regularInsights);
        rightColumn.add(predictiveInsights);
        content.add(leftColumn, rightColumn);

        // Add everything to main layout
        add(header, content);

        // Initial data load
        refreshInsights();
    }

    private void refreshInsights() {
        try {
            // 1. Retrieve regular insights from the existing service.
            List<SpendingInsight> allInsights = insightService.generateInsights();

            // 2. Get ML-based analysis for all accounts.
            List<AdvancedMLService.AccountAnalysis> analyses = mlService.analyzeAccounts();
            List<SpendingInsight> mlInsights = new ArrayList<>();

            // 3. Map ML analysis results to SpendingInsight objects.
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
                        // Optionally map frequency anomalies to recurring payments as well.
                        mlInsights.add(new SpendingInsight(
                            SpendingInsightType.RECURRING_PAYMENT,
                            anomaly.description(),
                            anomaly.severity()
                        ));
                    }
                }

                // --- Map recurring payments from merchant metrics ---
                analysis.getMerchantMetrics().forEach((merchant, metrics) -> {
                    // If the merchant has a high monthly frequency (e.g., at least one transaction per month)
                    if (metrics.monthlyFrequency() >= 1.0) {
                        mlInsights.add(new SpendingInsight(
                            SpendingInsightType.RECURRING_PAYMENT,
                            "Recurring payment detected at " + merchant + " (monthly frequency: " +
                                String.format("%.1f", metrics.monthlyFrequency()) + ")",
                            Math.min(1.0, metrics.monthlyFrequency() / 5.0)  // Arbitrary normalization
                        ));
                    }
                });

                // --- Map predicted expense from overall spending trend ---
                if (analysis.getVolumeTrend() > 0.2) { // Upward trend threshold (adjust as needed)
                    mlInsights.add(new SpendingInsight(
                        SpendingInsightType.PREDICTED_EXPENSE,
                        "Your spending is trending upward for account " + analysis.getAccount().getName() +
                            ". Consider revising your budget.",
                        0.8  // Example confidence value
                    ));
                }

                // --- Map budget suggestions from envelope metrics ---
                analysis.getEnvelopeMetrics().forEach((envelope, envMetrics) -> {
                    // If the envelope's budget utilization is high (e.g., over 90%)
                    if (envMetrics.budgetUtilization() > 0.9) {
                        // Assuming your Envelope domain object has a getName() method.
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
                if (analysis.getVolumeTrend() < -0.2) { // Downward trend threshold (adjust as needed)
                    mlInsights.add(new SpendingInsight(
                        SpendingInsightType.SEASONAL_PATTERN,
                        "Your spending trend indicates a seasonal downturn for account " +
                            analysis.getAccount().getName() + ".",
                        0.75
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

            // Merge regular insights with ML-based insights.
            allInsights.addAll(mlInsights);

            // 4. Split the insights into two categories: current and predictive.
            List<SpendingInsight> current = allInsights.stream()
                .filter(i -> i.getType() == SpendingInsightType.UNUSUAL_SPENDING ||
                             i.getType() == SpendingInsightType.RECURRING_PAYMENT ||
                             i.getType() == SpendingInsightType.BUDGET_SUGGESTION)
                .collect(Collectors.toList());

            List<SpendingInsight> predictive = allInsights.stream()
                .filter(i -> i.getType() == SpendingInsightType.PREDICTED_EXPENSE ||
                             i.getType() == SpendingInsightType.SEASONAL_PATTERN ||
                             i.getType() == SpendingInsightType.REALLOCATION_SUGGESTION)
                .collect(Collectors.toList());

            // 5. Update the InsightsPanel components.
            regularInsights.setInsights(current);
            predictiveInsights.setInsights(predictive);

        } catch (Exception e) {
            Notification.show(
                "Error loading insights: " + e.getMessage(),
                3000,
                Notification.Position.MIDDLE
            );
        }
    }


}
