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

import org.emblow.envelofy.ui.components.AccountDialog;
import org.emblow.envelofy.ui.components.AccountBalanceChart;
import org.emblow.envelofy.ui.components.TransactionManagementDialogs;
import org.emblow.envelofy.ui.components.SpendingTrendsChart_Accounts;
import org.emblow.envelofy.ui.components.SpendingCategoryChart;
import org.emblow.envelofy.ui.components.SpendingTrendsChart;
import org.emblow.envelofy.ui.components.CSVImportDialog;
import org.emblow.envelofy.service.ml.SpendingInsightType;
import org.emblow.envelofy.service.ml.SpendingInsight;
import org.emblow.envelofy.service.ml.AdvancedMLService;
import org.emblow.envelofy.service.ml.SpendingInsightService;
import org.emblow.envelofy.service.CSVImportService;
import org.emblow.envelofy.service.PatternService;
import org.emblow.envelofy.service.TransactionService;
import org.emblow.envelofy.service.EnvelopeService;
import org.emblow.envelofy.service.AccountService;
import org.emblow.envelofy.service.RecurringTransactionService;
import org.emblow.envelofy.domain.Account;
import org.emblow.envelofy.domain.Transaction;
import org.emblow.envelofy.domain.RecurringTransaction;
import org.emblow.envelofy.domain.TransactionType;
import org.emblow.envelofy.domain.Envelope;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import org.emblow.envelofy.exception.AccountException;
import org.emblow.envelofy.ui.MainLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Route(value = "accounts", layout = MainLayout.class)
@PageTitle("Account Details | Envelofy")
public class AccountView extends VerticalLayout implements HasUrlParameter<Long> {
    private static final Logger log = LoggerFactory.getLogger(AccountView.class);

    private final AccountService accountService;
    private final TransactionService transactionService;
    private final TransactionManagementDialogs transactionDialogs;
    private final CSVImportService csvImportService;
    private final SpendingInsightService spendingInsightService;
    private final AdvancedMLService advancedMLService;
    private final PatternService patternService;
    private final EnvelopeService envelopeService;
    private Tabs tabs; 
    private Account currentAccount;
    private final Grid<Transaction> transactionGrid;
    private AccountBalanceChart balanceChart;
    private SpendingTrendsChart trendsChart;
    private TextField searchField;
    private RecurringTransactionService recurringTransactionService;

    public AccountView(
        AccountService accountService,
        TransactionService transactionService,
        TransactionManagementDialogs transactionDialogs,
        CSVImportService csvImportService,
        SpendingInsightService spendingInsightService,
        AdvancedMLService advancedMLService,
        PatternService patternService,
        EnvelopeService envelopeService,
        RecurringTransactionService recurringTransactionService
    ) {
        this.accountService = accountService;
        this.transactionService = transactionService;
        this.transactionDialogs = transactionDialogs;
        this.csvImportService = csvImportService;
        this.spendingInsightService = spendingInsightService;
        this.advancedMLService = advancedMLService;
        this.patternService = patternService;
        this.envelopeService = envelopeService;
        this.recurringTransactionService = recurringTransactionService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        transactionGrid = new Grid<>();
        configureTransactionGrid();
    }

    @Override
    public void setParameter(BeforeEvent event, Long accountId) {
        try {
            log.info("Loading account with ID: {}", accountId);

            // First load the account
            currentAccount = accountService.getAccount(accountId);
            log.info("Successfully loaded account: {}", currentAccount.getName());

            // Now make sure all required services are available
            if (transactionService == null || envelopeService == null || 
                spendingInsightService == null || advancedMLService == null) {
                throw new IllegalStateException("Required services not initialized");
            }

            // Initialize the layout components
            log.info("Initializing view layout");
            updateView();
            log.info("View initialization complete");

        } catch (AccountException e) {
            log.error("Account access error for ID {}: {}", accountId, e.getMessage());
            Notification.show("Error: " + e.getMessage(), 3000, Notification.Position.MIDDLE);
            UI.getCurrent().navigate("dashboard");
        } catch (IllegalStateException e) {
            log.error("Unexpected error loading account {}: {}", accountId, e.getMessage(), e);
            Notification.show("Unexpected error loading account: " + e.getMessage(), 
                             5000, Notification.Position.MIDDLE);
            UI.getCurrent().navigate("dashboard");
        }
    }

    private void refreshSubscriptions() {
        // Find the pages container
        Optional<Component> pagesContainer = getChildren()
            .filter(c -> c instanceof Div && !((Div) c).getChildren().findAny().isEmpty())
            .skip(1) // Skip the first div (likely the account overview and action bar)
            .findFirst();

        if (!pagesContainer.isPresent()) {
        } else {
            // Get all pages
            List<Component> pages = ((Div) pagesContainer.get()).getChildren()
                    .collect(Collectors.toList());
            
            // The subscription page should be the 4th page (index 3)
            if (pages.size() > 3) {
                Component subscriptionsPage = pages.get(3);

                // Clear and recreate subscription panel
                if (subscriptionsPage instanceof Div div) {
                    div.removeAll();
                    div.add(createSubscriptionPanel());
                }
            }
        }

        // Also refresh account data to ensure we have the latest information
        currentAccount = accountService.getAccount(currentAccount.getId());
    }
    
    
 private void updateView() {
    removeAll();

    add(createAccountOverviewCard(), createActionBar());

    // Create tabs
    Tabs navigationTabs = new Tabs();
    Div pages = new Div();
    pages.setWidthFull();

    // Overview tab
    Tab overviewTab = new Tab("Overview");
    Div overviewPage = new Div();
    overviewPage.add(
        createChartSection(),
        createAnalysisSection()
    );

    // Transactions tab
    Tab transactionsTab = new Tab("Transactions");
    Div transactionsPage = new Div();
    transactionsPage.add(createTransactionSection());

    // Analytics tab
    Tab analyticsTab = new Tab("Analytics");
    Div analyticsPage = new Div();
    analyticsPage.add(
        createBudgetHealthSection(),
        createSpendingPatternsSection()
    );
    
    // Subscriptions tab - NEW!
    Tab subscriptionsTab = new Tab("Subscriptions");
    Div subscriptionsPage = new Div();
    subscriptionsPage.add(createSubscriptionPanel());

    // AI Insights tab
    Tab insightsTab = new Tab("AI Insights");
    Div insightsPage = new Div();
    insightsPage.add(
        createAIInsightsSection(),
        createAnomalySection(),
        createEnvelopeRecommendationsSection()
    );

    navigationTabs.add(overviewTab, transactionsTab, analyticsTab, subscriptionsTab, insightsTab);
    pages.add(overviewPage, transactionsPage, analyticsPage, subscriptionsPage, insightsPage);

    // Set up tab behavior
    for (Component page : pages.getChildren().collect(Collectors.toList())) {
        page.setVisible(false);
    }
    overviewPage.setVisible(true);

    navigationTabs.addSelectedChangeListener(event -> {
        for (Component page : pages.getChildren().collect(Collectors.toList())) {
            page.setVisible(false);
        }
        if (event.getSelectedTab().equals(overviewTab)) {
            overviewPage.setVisible(true);
        } else if (event.getSelectedTab().equals(transactionsTab)) {
            transactionsPage.setVisible(true);
        } else if (event.getSelectedTab().equals(analyticsTab)) {
            analyticsPage.setVisible(true);
        } else if (event.getSelectedTab().equals(subscriptionsTab)) {
            subscriptionsPage.setVisible(true);
        } else if (event.getSelectedTab().equals(insightsTab)) {
            insightsPage.setVisible(true);
        }
    });

    add(navigationTabs, pages);
    updateTransactionList(null,"");
}

    private Div createAccountOverviewCard() {
        Div card = new Div();
        card.addClassName("account-overview-card");
        card.getStyle()
            .set("background-color", "var(--lumo-base-color)")
            .set("border-radius", "var(--lumo-border-radius-l)")
            .set("padding", "var(--lumo-space-l)")
            .set("margin-bottom", "var(--lumo-space-m)")
            .set("box-shadow", "var(--lumo-box-shadow-xs)");

        H2 accountName = new H2(currentAccount.getName());
        
        Span typeBadge = new Span(currentAccount.getType().getDisplayName());
        typeBadge.getStyle()
            .set("background-color", "var(--lumo-primary-color-10pct)")
            .set("color", "var(--lumo-primary-text-color)")
            .set("padding", "var(--lumo-space-xs) var(--lumo-space-s)")
            .set("border-radius", "var(--lumo-border-radius-m)")
            .set("font-size", "var(--lumo-font-size-s)");

        Span balance = new Span(String.format("Balance: $%.2f", currentAccount.getBalance()));
        balance.getStyle()
            .set("font-size", "var(--lumo-font-size-xl)")
            .set("font-weight", "bold")
            .set("color", currentAccount.getBalance().signum() >= 0 ? 
                "var(--lumo-success-text-color)" : 
                "var(--lumo-error-text-color)");

        if (currentAccount.getInstitution() != null) {
            Span institution = new Span(currentAccount.getInstitution());
            institution.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)");
            card.add(institution);
        }

        card.add(accountName, typeBadge, balance);
        return card;
    }

    private HorizontalLayout createActionBar() {
        HorizontalLayout actionBar = new HorizontalLayout();
        actionBar.setWidthFull();
        actionBar.setSpacing(true);
        actionBar.setPadding(true);
        actionBar.getStyle()
            .set("background-color", "var(--lumo-contrast-5pct)")
            .set("border-radius", "var(--lumo-border-radius-m)")
            .set("margin-bottom", "var(--lumo-space-m)");

        Button newTransactionBtn = new Button(
            "New Transaction",
            new Icon(VaadinIcon.PLUS),
            e -> transactionDialogs.showNewTransactionDialog(
                this::refreshData, 
                currentAccount
            )
        );
        newTransactionBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button importBtn = new Button(
            "Import Transactions",
            new Icon(VaadinIcon.UPLOAD),
            e -> new CSVImportDialog(
                csvImportService,
                envelopeService,
                accountService,  
                this::refreshData
            ).open()
        );

        Button editBtn = new Button(
            "Edit Account",
            new Icon(VaadinIcon.EDIT),
            e -> new AccountDialog(
                accountService,
                this::refreshData,
                currentAccount
            ).open()
        );

        actionBar.add(newTransactionBtn, importBtn, editBtn);
        return actionBar;
    }

    private Div createChartSection() {
        Div chartSection = new Div();
        chartSection.setWidthFull();
        chartSection.getStyle()
            .set("background-color", "var(--lumo-base-color)")
            .set("border-radius", "var(--lumo-border-radius-l)")
            .set("padding", "var(--lumo-space-m)")
            .set("margin-bottom", "var(--lumo-space-m)")
            .set("box-shadow", "var(--lumo-box-shadow-xs)")
            .set("display", "flex")           // Ensure layout control
            .set("flex-direction", "column"); // Stack children vertically

        HorizontalLayout topCharts = new HorizontalLayout();
        topCharts.setWidthFull();
        topCharts.setSpacing(true);

        Div balanceChartDiv = new Div();
        balanceChartDiv.setWidthFull();
        H3 balanceTitle = new H3("Balance History");
        balanceChart = new AccountBalanceChart(currentAccount);
        balanceChart.getElement().getStyle().set("height", "300px");
        balanceChartDiv.add(balanceTitle, balanceChart);

        Div categoryChartDiv = new Div();
        categoryChartDiv.setWidthFull();
        H3 categoryTitle = new H3("Spending by Category");
        SpendingCategoryChart categoryChart = new SpendingCategoryChart(currentAccount);
        categoryChart.getElement().getStyle().set("height", "300px");
        categoryChartDiv.add(categoryTitle, categoryChart);

        topCharts.add(balanceChartDiv, categoryChartDiv);

        H3 trendsTitle = new H3("Monthly Spending Trends");
        SpendingTrendsChart_Accounts trendsChart = new SpendingTrendsChart_Accounts(currentAccount);
        trendsChart.getElement().getStyle()
            .set("height", "300px")
            .set("flex-grow", "1"); // Allow it to take available space

        chartSection.add(topCharts, trendsTitle, trendsChart);
        chartSection.setHeight("auto"); // Let content dictate height
        return chartSection;
    }

    private Div createAIInsightsSection() {
        Div section = new Div();
        section.setWidthFull();
        section.getStyle()
            .set("background-color", "var(--lumo-base-color)")
            .set("border-radius", "var(--lumo-border-radius-l)")
            .set("padding", "var(--lumo-space-m)")
            .set("margin-bottom", "var(--lumo-space-m)");

        List<SpendingInsight> insights = spendingInsightService.generateInsights()
            .stream()
            .filter(i -> i.getMessage().contains(currentAccount.getName()))
            .collect(Collectors.toList());

        AdvancedMLService.AccountAnalysis analysis = advancedMLService.analyzeAccounts()
            .stream()
            .filter(a -> a.getAccount().getId().equals(currentAccount.getId()))
            .findFirst()
            .orElse(null);

        List<AdvancedMLService.AnomalyDetection> anomalies = 
            advancedMLService.detectAnomaliesForAccount(currentAccount.getTransactions());

        // Add summary at the top
        section.add(createInsightSummary(insights, analysis, anomalies));

        if (!insights.isEmpty()) {
            insights.forEach(insight -> {
                Div card = new Div();
                card.addClassName("insight-card");
                card.getStyle()
                    .set("padding", "var(--lumo-space-m)")
                    .set("margin-bottom", "var(--lumo-space-s)")
                    .set("border-radius", "var(--lumo-border-radius-m)")
                    .set("background-color", "var(--lumo-contrast-5pct)");

                Icon icon = getInsightIcon(insight.getType());
                icon.setColor(getInsightColor(insight.getType()));

                Span message = new Span(insight.getMessage());
                message.getStyle().set("margin-left", "var(--lumo-space-s)");

                ProgressBar confidence = new ProgressBar();
                confidence.setValue(insight.getConfidence());
                confidence.setWidth("100%");

                card.add(new HorizontalLayout(icon, message), confidence);
                section.add(card);
            });
        }

        if (analysis != null && !analysis.getMerchantMetrics().isEmpty()) {
            H4 merchantTitle = new H4("Top Merchant Analysis");
            Grid<Map.Entry<String, AdvancedMLService.MerchantMetrics>> merchantGrid = new Grid<>();
            
            merchantGrid.setItems(analysis.getMerchantMetrics().entrySet().stream()
                .sorted((e1, e2) -> Double.compare(e2.getValue().totalSpent(), e1.getValue().totalSpent()))
                .limit(5)
                .collect(Collectors.toList()));

            merchantGrid.addColumn(Map.Entry::getKey)
                .setHeader("Merchant")
                .setAutoWidth(true);
            merchantGrid.addColumn(e -> String.format("$%.2f", e.getValue().totalSpent()))
                .setHeader("Total Spent")
                .setAutoWidth(true);
            merchantGrid.addColumn(e -> String.format("%.1f/month", e.getValue().monthlyFrequency()))
                .setHeader("Frequency")
                .setAutoWidth(true);

            section.add(merchantTitle, merchantGrid);
        }

        return section;
    }

    private Div createBudgetHealthSection() {
        Div section = new Div();
        section.setWidthFull();
        section.getStyle()
            .set("background-color", "var(--lumo-base-color)")
            .set("border-radius", "var(--lumo-border-radius-l)")
            .set("padding", "var(--lumo-space-m)")
            .set("margin-bottom", "var(--lumo-space-m)");

        AdvancedMLService.AccountAnalysis analysis = advancedMLService.analyzeAccounts()
            .stream()
            .filter(a -> a.getAccount().getId().equals(currentAccount.getId()))
            .findFirst()
            .orElse(null);

        if (analysis != null) {
            H3 title = new H3("Budget Health");

            // Balance trend indicator
            Div trendSection = new Div();
            String trendDescription = analysis.getVolumeTrend() > 0 ? 
                "Increasing" : analysis.getVolumeTrend() < 0 ? "Decreasing" : "Stable";
            trendSection.add(
                new H4("Balance Trend: " + trendDescription),
                new Span(String.format("%.1f%% per month", analysis.getVolumeTrend() * 100))
            );

            // Monthly volume
            Div volumeSection = new Div();
            volumeSection.add(
                new H4("Average Monthly Volume"),
                new Span(String.format("$%.2f", analysis.getAverageMonthlyVolume()))
            );

            section.add(title, trendSection, volumeSection);

            // Add envelope metrics if available
            if (!analysis.getEnvelopeMetrics().isEmpty()) {
                H4 envelopeTitle = new H4("Envelope Health");
                Grid<Map.Entry<Envelope, AdvancedMLService.EnvelopeMetrics>> envelopeGrid = new Grid<>();
                
                envelopeGrid.setItems(analysis.getEnvelopeMetrics().entrySet());
                envelopeGrid.addColumn(e -> e.getKey().getName())
                    .setHeader("Envelope")
                    .setAutoWidth(true);
                envelopeGrid.addColumn(e -> String.format("%.1f%%", e.getValue().budgetUtilization() * 100))
                    .setHeader("Budget Utilization")
                    .setAutoWidth(true);
                envelopeGrid.addColumn(e -> String.format("$%.2f", e.getValue().totalSpent()))
                    .setHeader("Total Spent")
                    .setAutoWidth(true);
                envelopeGrid.addColumn(e -> {
                    double trend = e.getValue().spendingTrend();
                    return String.format("%s%.1f%%", trend > 0 ? "+" : "", trend * 100);
                })
                    .setHeader("Trend")
                    .setAutoWidth(true);

                section.add(envelopeTitle, envelopeGrid);
            }
        }

        return section;
    }

    private Div createSpendingPatternsSection() {
        Div section = new Div();
        section.setWidthFull();
        section.getStyle()
            .set("background-color", "var(--lumo-base-color)")
            .set("border-radius", "var(--lumo-border-radius-l)")
            .set("padding", "var(--lumo-space-m)")
            .set("margin-bottom", "var(--lumo-space-m)");

        H3 title = new H3("Spending Patterns");

        // Get account analysis
        AdvancedMLService.AccountAnalysis analysis = advancedMLService.analyzeAccounts()
            .stream()
            .filter(a -> a.getAccount().getId().equals(currentAccount.getId()))
            .findFirst()
            .orElse(null);

        if (analysis != null) {
            // Day of week patterns
            H4 dowTitle = new H4("Day of Week Patterns");
            Grid<Map.Entry<java.time.DayOfWeek, Double>> dowGrid = new Grid<>();
            dowGrid.setItems(analysis.getDayOfWeekPatterns().entrySet());
            dowGrid.addColumn(e -> e.getKey().toString())
                .setHeader("Day")
                .setAutoWidth(true);
            dowGrid.addColumn(e -> String.format("$%.2f", e.getValue()))
                .setHeader("Average Spending")
                .setAutoWidth(true);

            // Merchant patterns
            H4 merchantTitle = new H4("Merchant Patterns");
            Grid<Map.Entry<String, AdvancedMLService.MerchantMetrics>> merchantGrid = new Grid<>();
            merchantGrid.setItems(analysis.getMerchantMetrics().entrySet().stream()
                .filter(e -> e.getValue().monthlyFrequency() > 0.5)
                .collect(Collectors.toList()));
            merchantGrid.addColumn(Map.Entry::getKey)
                .setHeader("Merchant")
                .setAutoWidth(true);
            merchantGrid.addColumn(e -> String.format("Every %.1f days", e.getValue().averageDaysBetween()))
                .setHeader("Frequency")
                .setAutoWidth(true);
            merchantGrid.addColumn(e -> String.format("$%.2f", e.getValue().averageAmount()))
                .setHeader("Average Amount")
                .setAutoWidth(true);

            section.add(title, dowTitle, dowGrid, merchantTitle, merchantGrid);
        }

        return section;
    }

    private Div createAnomalySection() {
        Div section = new Div();
        section.setWidthFull();
        section.getStyle()
            .set("background-color", "var(--lumo-base-color)")
            .set("border-radius", "var(--lumo-border-radius-l)")
            .set("padding", "var(--lumo-space-m)")
            .set("margin-bottom", "var(--lumo-space-m)");

        H3 title = new H3("Anomaly Detection");

        List<AdvancedMLService.AnomalyDetection> anomalies = 
            advancedMLService.detectAnomaliesForAccount(currentAccount.getTransactions());

        if (!anomalies.isEmpty()) {
            Grid<AdvancedMLService.AnomalyDetection> grid = new Grid<>();
            grid.setItems(anomalies);
            
            grid.addColumn(AdvancedMLService.AnomalyDetection::description)
                .setHeader("Description")
                .setAutoWidth(true);
            grid.addColumn(a -> String.format("%.1f", a.severity()))
                .setHeader("Severity")
                .setAutoWidth(true);
            grid.addComponentColumn(anomaly -> {
                ProgressBar severity = new ProgressBar();
                severity.setValue(anomaly.severity() / 5.0); // Normalize to 0-1
                return severity;
            })
                .setHeader("Impact")
                .setAutoWidth(true);

            section.add(title, grid);
        } else {
            Span noAnomalies = new Span("No anomalies detected");
            noAnomalies.getStyle().set("color", "var(--lumo-success-text-color)");
            section.add(title, noAnomalies);
        }

        return section;
    }

    private Div createEnvelopeRecommendationsSection() {
        Div section = new Div();
        section.setWidthFull();
        section.getStyle()
            .set("background-color", "var(--lumo-base-color)")
            .set("border-radius", "var(--lumo-border-radius-l)")
            .set("padding", "var(--lumo-space-m)")
            .set("margin-bottom", "var(--lumo-space-m)");

        H3 title = new H3("Envelope Recommendations");

        // Get suggestions for recent transactions
        List<Transaction> recentTransactions = transactionService.getRecentTransactions(
            LocalDateTime.now().minusWeeks(2),
            LocalDateTime.now()
        );

        if (!recentTransactions.isEmpty()) {
            Grid<Transaction> grid = new Grid<>();
            grid.setItems(recentTransactions);
            
            grid.addColumn(Transaction::getDescription)
                .setHeader("Transaction")
                .setAutoWidth(true);
            grid.addColumn(tx -> String.format("$%.2f", tx.getAmount()))
                .setHeader("Amount")
                .setAutoWidth(true);
            grid.addColumn(tx -> tx.getEnvelope().getName())
                .setHeader("Current Envelope")
                .setAutoWidth(true);
            grid.addComponentColumn(tx -> {
                // Get envelope suggestions
                Map<Envelope, Double> suggestions = patternService.suggestEnvelopes(tx);
                if (!suggestions.isEmpty()) {
                    Map.Entry<Envelope, Double> topSuggestion = suggestions.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .get();
                    
                    if (!topSuggestion.getKey().equals(tx.getEnvelope())) {
                        Button changeBtn = new Button(
                            String.format("Change to %s (%.0f%% match)", 
                                topSuggestion.getKey().getName(),
                                topSuggestion.getValue() * 100
                            ),
                            e -> {
                                // Update transaction envelope
                                transactionService.updateTransaction(
                                    tx.getId(),
                                    topSuggestion.getKey().getId(),
                                    tx.getAccount().getId(),
                                    tx.getDate(),
                                    tx.getDescription(),
                                    tx.getAmount(),
                                    tx.getType()
                                );
                                refreshData();
                            }
                        );
                        changeBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);
                        return changeBtn;
                    }
                }
                return new Span("Best match");
            })
                .setHeader("Suggestion")
                .setAutoWidth(true);

            section.add(title, grid);
        } else {
            section.add(title, new Span("No recent transactions to analyze"));
        }

        return section;
    }

    private Div createAnalysisSection() {
        Div section = new Div();
        section.setWidthFull();
        section.getStyle()
            .set("display", "flex")
            .set("gap", "var(--lumo-space-m)")
            .set("margin-bottom", "var(--lumo-space-m)");

        // Monthly spending card
        Div spendingCard = createMetricCard(
            "Monthly Spending",
            calculateMonthlySpending(),
            "var(--lumo-primary-color)"
        );

        // Income card
        Div incomeCard = createMetricCard(
            "Monthly Income",
            calculateMonthlyIncome(),
            "var(--lumo-success-color)"
        );

        // Transaction count card
        Div countCard = createMetricCard(
            "Transaction Count",
            String.valueOf(currentAccount.getTransactions().size()),
            "var(--lumo-contrast-color)"
        );

        section.add(spendingCard, incomeCard, countCard);
        return section;
    }

    private VerticalLayout createTransactionSection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(true);
        
        // Add filtering controls
        HorizontalLayout filterBar = new HorizontalLayout();
        
        DatePicker startDate = new DatePicker("Start Date");
        DatePicker endDate = new DatePicker("End Date");
        
        searchField = new TextField();
        searchField.setPlaceholder("Search transactions...");
        searchField.setPrefixComponent(new Icon(VaadinIcon.SEARCH));
        
        searchField.addValueChangeListener(e -> {
            // Get current selected tab for transaction type
            Tab selectedTab = tabs.getSelectedTab();
            TransactionType type = null;
            if (selectedTab != null) {
                switch (selectedTab.getLabel()) {
                    case "Income" -> type = TransactionType.INCOME;
                    case "Expenses" -> type = TransactionType.EXPENSE;
                }
            }
            
            // Apply date filters and search
            String searchPattern = e.getValue();
            List<Transaction> filtered = currentAccount.getTransactions().stream()
                .filter(tx -> {
                    boolean matchesDate = true;
                    if (startDate.getValue() != null) {
                        matchesDate = !tx.getDate().toLocalDate().isBefore(startDate.getValue());
                    }
                    if (endDate.getValue() != null) {
                        matchesDate = matchesDate && !tx.getDate().toLocalDate().isAfter(endDate.getValue());
                    }
                    return matchesDate;
                })
                .collect(Collectors.toList());
                
            transactionGrid.setItems(filtered);
            updateTransactionList(type, searchPattern);
        });

        Button filterButton = new Button("Apply Filters", e -> {
            searchField.getElement().callJsFunction("focus");
            searchField.setValue(searchField.getValue()); // Trigger search with date filters
        });
        
        filterBar.add(startDate, endDate, searchField, filterButton);
        
        // Add transaction tabs
        tabs = new Tabs();
        Tab allTab = new Tab("All Transactions");
        Tab incomingTab = new Tab("Income");
        Tab outgoingTab = new Tab("Expenses");
        tabs.add(allTab, incomingTab, outgoingTab);

        tabs.addSelectedChangeListener(event -> {
            switch (event.getSelectedTab().getLabel()) {
                case "All Transactions" -> updateTransactionList(null, searchField.getValue());
                case "Income" -> updateTransactionList(TransactionType.INCOME, searchField.getValue());
                case "Expenses" -> updateTransactionList(TransactionType.EXPENSE, searchField.getValue());
            }
        });

        section.add(filterBar, tabs, transactionGrid);
        return section;
    }
    
    private Div createInsightSummary(List<SpendingInsight> insights, 
                                    AdvancedMLService.AccountAnalysis analysis, 
                                    List<AdvancedMLService.AnomalyDetection> anomalies) {
        Div summaryCard = new Div();
        summaryCard.getStyle()
            .set("background-color", "var(--lumo-contrast-5pct)")
            .set("border-radius", "var(--lumo-border-radius-m)")
            .set("padding", "var(--lumo-space-m)")
            .set("margin-bottom", "var(--lumo-space-l)");

        H4 summaryTitle = new H4("Key Takeaways");
        summaryCard.add(summaryTitle);

        // Build summary points
        VerticalLayout points = new VerticalLayout();
        points.setSpacing(true);
        points.setPadding(false);

        // Spending trends
        if (analysis != null) {
            String trendDescription = analysis.getVolumeTrend() > 0.1 ? 
                "increasing" : analysis.getVolumeTrend() < -0.1 ? 
                "decreasing" : "stable";
            double trendPercent = Math.abs(analysis.getVolumeTrend() * 100);

            Span trend = new Span(String.format("📈 Spending is %s (%.1f%% per month)", 
                trendDescription, trendPercent));
            points.add(trend);
        }

        // Most significant insights
        insights.stream()
            .filter(i -> i.getConfidence() > 0.8)  // Only high confidence insights
            .forEach(insight -> {
                String emoji = switch (insight.getType()) {
                    case RECURRING_PAYMENT -> "🔄";
                    case UNUSUAL_SPENDING -> "⚠️";
                    case PREDICTED_EXPENSE -> "📅";
                    case BUDGET_SUGGESTION -> "💰";
                    case SEASONAL_PATTERN -> "📊";
                    case REALLOCATION_SUGGESTION -> "🔄";
                };

                // Convert message to more concise format
                String message = insight.getMessage()
                    .replaceAll("detected in", "in")
                    .replaceAll("Consider ", "")
                    .replaceAll(" recommended", "");

                points.add(new Span(emoji + " " + message));
            });

        // Anomalies summary
        if (!anomalies.isEmpty()) {
            // Group anomalies by type
            long amountAnomalies = anomalies.stream()
                .filter(a -> a.type() == AdvancedMLService.AnomalyType.AMOUNT)
                .count();
            long freqAnomalies = anomalies.stream()
                .filter(a -> a.type() == AdvancedMLService.AnomalyType.FREQUENCY)
                .count();

            if (amountAnomalies > 0) {
                points.add(new Span(String.format("⚠️ Found %d unusual transaction amount%s", 
                    amountAnomalies, amountAnomalies > 1 ? "s" : "")));
            }
            if (freqAnomalies > 0) {
                points.add(new Span(String.format("📊 Found %d unusual spending pattern%s", 
                    freqAnomalies, freqAnomalies > 1 ? "s" : "")));
            }
        }

        // Add top merchants if available
        if (analysis != null && !analysis.getMerchantMetrics().isEmpty()) {
            String topMerchant = analysis.getMerchantMetrics().entrySet().stream()
                .max(Map.Entry.comparingByValue(Comparator.comparingDouble(
                    AdvancedMLService.MerchantMetrics::totalSpent)))
                .map(Map.Entry::getKey)
                .orElse(null);

            if (topMerchant != null) {
                points.add(new Span(String.format("🏪 Highest spending at %s", topMerchant)));
            }
        }

        // Add recommendations count
        long recommendationCount = insights.stream()
            .filter(i -> i.getType() == SpendingInsightType.BUDGET_SUGGESTION ||
                        i.getType() == SpendingInsightType.REALLOCATION_SUGGESTION)
            .count();
        if (recommendationCount > 0) {
            points.add(new Span(String.format("💡 %d budget optimization suggestion%s", 
                recommendationCount, recommendationCount > 1 ? "s" : "")));
        }

        summaryCard.add(points);
        return summaryCard;
    }
    private void configureTransactionGrid() {
        transactionGrid.addColumn(tx -> 
            tx.getDate().format(DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm")))
            .setHeader("Date")
            .setSortable(true)
            .setAutoWidth(true);
            
        transactionGrid.addColumn(Transaction::getDescription)
            .setHeader("Description")
            .setAutoWidth(true);
            
        transactionGrid.addColumn(tx -> String.format("$%.2f", tx.getAmount()))
            .setHeader("Amount")
            .setAutoWidth(true);
            
        transactionGrid.addColumn(tx -> tx.getType().toString())
            .setHeader("Type")
            .setAutoWidth(true);
            
        transactionGrid.addColumn(tx -> tx.getEnvelope().getName())
            .setHeader("Envelope")
            .setAutoWidth(true);

        transactionGrid.addItemDoubleClickListener(event -> {
            transactionDialogs.showEditTransactionDialog(event.getItem(), this::refreshData);
        });

        transactionGrid.setClassNameGenerator(tx -> {
            return tx.getType() == TransactionType.INCOME ? "income-row" : "expense-row";
        });
    }


    private Icon getInsightIcon(SpendingInsightType type) {
        return switch (type) {
            case RECURRING_PAYMENT -> VaadinIcon.REFRESH.create();
            case UNUSUAL_SPENDING -> VaadinIcon.EXCLAMATION_CIRCLE.create();
            case PREDICTED_EXPENSE -> VaadinIcon.TRENDING_UP.create();
            case BUDGET_SUGGESTION -> VaadinIcon.PIGGY_BANK.create();
            case SEASONAL_PATTERN -> VaadinIcon.CALENDAR.create();
            case REALLOCATION_SUGGESTION -> VaadinIcon.EXCHANGE.create();
        };
    }

    private String getInsightColor(SpendingInsightType type) {
        return switch (type) {
            case UNUSUAL_SPENDING -> "var(--lumo-error-color)";
            case BUDGET_SUGGESTION -> "var(--lumo-success-color)";
            case PREDICTED_EXPENSE -> "var(--lumo-primary-color)";
            default -> "var(--lumo-contrast)";
        };
    }

    private Div createMetricCard(String title, String value, String color) {
        Div card = new Div();
        card.getStyle()
            .set("background-color", "var(--lumo-base-color)")
            .set("border-radius", "var(--lumo-border-radius-l)")
            .set("padding", "var(--lumo-space-m)")
            .set("flex", "1")
            .set("text-align", "center")
            .set("box-shadow", "var(--lumo-box-shadow-xs)");

        H3 titleElem = new H3(title);
        titleElem.getStyle()
            .set("margin", "0")
            .set("color", "var(--lumo-secondary-text-color)")
            .set("font-size", "var(--lumo-font-size-s)");

        Span valueElem = new Span(value);
        valueElem.getStyle()
            .set("font-size", "var(--lumo-font-size-xxl)")
            .set("font-weight", "bold")
            .set("color", color);

        card.add(titleElem, valueElem);
        return card;
    }

    private String calculateMonthlySpending() {
        BigDecimal total = currentAccount.getTransactions().stream()
            .filter(tx -> tx.getType() == TransactionType.EXPENSE)
            .filter(tx -> tx.getDate().getMonth() == LocalDateTime.now().getMonth())
            .map(Transaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return String.format("$%.2f", total);
    }

    private String calculateMonthlyIncome() {
        BigDecimal total = currentAccount.getTransactions().stream()
            .filter(tx -> tx.getType() == TransactionType.INCOME)
            .filter(tx -> tx.getDate().getMonth() == LocalDateTime.now().getMonth())
            .map(Transaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return String.format("$%.2f", total);
    }
private Div createSubscriptionPanel() {
    Div section = new Div();
    section.setWidthFull();
    section.getStyle()
        .set("background-color", "var(--lumo-base-color)")
        .set("border-radius", "var(--lumo-border-radius-l)")
        .set("padding", "var(--lumo-space-m)")
        .set("margin-bottom", "var(--lumo-space-m)")
        .set("box-shadow", "var(--lumo-box-shadow-xs)");

    H3 title = new H3("Subscription Tracker");
    
    // Container for the subscription cards
    Div subscriptionsContainer = new Div();
    subscriptionsContainer.getStyle()
        .set("display", "flex")
        .set("flex-wrap", "wrap")
        .set("gap", "var(--lumo-space-m)");
    
    // Get detected recurring transactions from multiple sources
    List<SubscriptionInfo> subscriptions = detectSubscriptions();
    
    if (!subscriptions.isEmpty()) {
        // Create summary stats
        Div summaryCard = createSubscriptionSummary(subscriptions);
        
        // Create cards for each subscription
        subscriptions.forEach(subscription -> {
            subscriptionsContainer.add(createSubscriptionCard(subscription));
        });
        
        section.add(title, summaryCard, subscriptionsContainer);
    } else {
        Span noSubscriptions = new Span("No subscriptions detected for this account.");
        noSubscriptions.getStyle().set("color", "var(--lumo-secondary-text-color)");
        section.add(title, noSubscriptions);
    }
    
    return section;
}

private List<SubscriptionInfo> detectSubscriptions() {
    List<SubscriptionInfo> subscriptionInfos = new ArrayList<>();
    
    try {
        // Get user-recorded recurring transactions
        List<RecurringTransaction> userRecorded = new ArrayList<>();
        try {
            userRecorded = recurringTransactionService.getAllForAccount(currentAccount.getId());
        } catch (Exception e) {
            log.warn("Could not fetch user's recurring transactions", e);
        }
        
        // Map of merchant descriptions to user-recorded RecurringTransactions
        Map<String, RecurringTransaction> userRecordedByDescription = userRecorded.stream()
            .collect(Collectors.toMap(
                RecurringTransaction::getDescription,
                rt -> rt,
                (rt1, rt2) -> rt1 // In case of duplicate descriptions, keep the first one
            ));
            
        // 1. Get ML-detected recurring payments from SpendingInsightService
        List<SpendingInsight> insights = spendingInsightService.generateInsights()
            .stream()
            .filter(i -> i.getType() == SpendingInsightType.RECURRING_PAYMENT)
            .filter(i -> i.getMessage().contains(currentAccount.getName()))
            .collect(Collectors.toList());
            
        // Parse insights to extract merchant information
        for (SpendingInsight insight : insights) {
            String message = insight.getMessage();
            
            // Extract merchant name with regex
            Pattern merchantPattern = Pattern.compile("payments? (?:of \\$[\\d.]+)? to ([\\w\\s&'\\-]+) detected");
            Matcher merchantMatcher = merchantPattern.matcher(message);
            
            // Extract amount with regex
            Pattern amountPattern = Pattern.compile("\\$(\\d+\\.\\d+)");
            Matcher amountMatcher = amountPattern.matcher(message);
            
            // Extract frequency with regex  
            Pattern frequencyPattern = Pattern.compile("every (\\d+\\.\\d+) days");
            Matcher frequencyMatcher = frequencyPattern.matcher(message);
            
            if (merchantMatcher.find() && amountMatcher.find() && frequencyMatcher.find()) {
                String merchant = merchantMatcher.group(1).trim();
                BigDecimal amount = new BigDecimal(amountMatcher.group(1));
                double frequency = Double.parseDouble(frequencyMatcher.group(1));
                
                // Check if this matches a user-recorded recurring transaction
                boolean isUserRecorded = userRecordedByDescription.containsKey(merchant);
                RecurringTransaction matchingRecurring = userRecordedByDescription.get(merchant);
                
                // Get last transaction for this merchant
                Optional<Transaction> lastTransaction = currentAccount.getTransactions().stream()
                    .filter(tx -> tx.getDescription().equals(merchant))
                    .max(Comparator.comparing(Transaction::getDate));
                
                SubscriptionInfo info = new SubscriptionInfo(
                    merchant,
                    amount,
                    frequency,
                    lastTransaction.map(Transaction::getDate).orElse(null),
                    calculateNextDate(lastTransaction.map(Transaction::getDate).orElse(null), frequency),
                    insight.getConfidence(),
                    isUserRecorded,
                    matchingRecurring
                );
                
                subscriptionInfos.add(info);
            }
        }
        
        // 2. Get merchant analysis from AdvancedMLService
        AdvancedMLService.AccountAnalysis analysis = advancedMLService.analyzeAccounts()
            .stream()
            .filter(a -> a.getAccount().getId().equals(currentAccount.getId()))
            .findFirst()
            .orElse(null);
            
        if (analysis != null) {
            for (Map.Entry<String, AdvancedMLService.MerchantMetrics> entry : 
                 analysis.getMerchantMetrics().entrySet()) {
                
                String merchant = entry.getKey();
                AdvancedMLService.MerchantMetrics metrics = entry.getValue();
                
                // Only include merchants with regular recurrence that aren't already added
                if (metrics.averageDaysBetween() > 0 && 
                    metrics.averageDaysBetween() < 32 && // Monthly or more frequent
                    metrics.monthlyFrequency() >= 1 &&   // At least once per month
                    !subscriptionInfos.stream().anyMatch(si -> si.merchant.equals(merchant))) {
                    
                    // Check if this matches a user-recorded recurring transaction
                    boolean isUserRecorded = userRecordedByDescription.containsKey(merchant);
                    RecurringTransaction matchingRecurring = userRecordedByDescription.get(merchant);
                    
                    // Get last transaction for this merchant
                    Optional<Transaction> lastTransaction = currentAccount.getTransactions().stream()
                        .filter(tx -> tx.getDescription().equals(merchant))
                        .max(Comparator.comparing(Transaction::getDate));
                    
                    SubscriptionInfo info = new SubscriptionInfo(
                        merchant,
                        BigDecimal.valueOf(metrics.averageAmount()),
                        metrics.averageDaysBetween(),
                        lastTransaction.map(Transaction::getDate).orElse(null),
                        calculateNextDate(lastTransaction.map(Transaction::getDate).orElse(null), 
                            metrics.averageDaysBetween()),
                        0.7, // Default confidence for ML detection
                        isUserRecorded,
                        matchingRecurring
                    );
                    
                    subscriptionInfos.add(info);
                }
            }
        }
        
        // Add any user-recorded recurring transactions that weren't detected
        for (RecurringTransaction rt : userRecorded) {
            if (rt.getType() == TransactionType.EXPENSE && 
                !subscriptionInfos.stream().anyMatch(si -> si.merchant.equals(rt.getDescription()))) {
                
                // Convert RecurrencePattern to average days
                double frequencyDays = switch (rt.getPattern()) {
                    case DAILY -> 1.0;
                    case WEEKLY -> 7.0;
                    case BIWEEKLY -> 14.0;
                    case MONTHLY -> 30.4;
                    case YEARLY -> 365.25;
                };
                
                SubscriptionInfo info = new SubscriptionInfo(
                    rt.getDescription(),
                    rt.getAmount(),
                    frequencyDays,
                    rt.getLastProcessed(),
                    rt.getNextDueDate(),
                    1.0, // User-recorded so 100% confidence
                    true,
                    rt
                );
                
                subscriptionInfos.add(info);
            }
        }
    } catch (NumberFormatException e) {
        log.error("Error detecting subscriptions", e);
    }
    
    return subscriptionInfos;
}

private LocalDateTime calculateNextDate(LocalDateTime lastDate, double frequencyDays) {
    if (lastDate == null) {
        return null;
    }
    
    long daysDelta = Math.round(frequencyDays);
    return lastDate.plusDays(daysDelta);
}

private Div createSubscriptionSummary(List<SubscriptionInfo> subscriptions) {
    Div summaryCard = new Div();
    summaryCard.getStyle()
        .set("background-color", "var(--lumo-contrast-5pct)")
        .set("border-radius", "var(--lumo-border-radius-m)")
        .set("padding", "var(--lumo-space-m)")
        .set("margin-bottom", "var(--lumo-space-m)")
        .set("width", "100%");
    
    // Calculate total monthly cost
    BigDecimal monthlyTotal = subscriptions.stream()
        .map(sub -> {
            // Convert frequency to monthly multiplier
            double monthlyMultiplier = 30.4 / sub.frequencyDays;
            return sub.amount.multiply(BigDecimal.valueOf(monthlyMultiplier));
        })
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    
    // Count total subscriptions
    int totalCount = subscriptions.size();
    
    // Count untracked subscriptions
    long untrackedCount = subscriptions.stream()
        .filter(sub -> !sub.isUserRecorded)
        .count();
    
    H4 summaryTitle = new H4("Subscription Summary");
    
    HorizontalLayout stats = new HorizontalLayout();
    stats.setWidthFull();
    stats.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
    
    Div totalCostDiv = new Div();
    H5 totalCostLabel = new H5("Est. Monthly Cost");
    Span totalCostValue = new Span(String.format("$%.2f", monthlyTotal));
    totalCostValue.getStyle()
        .set("font-size", "var(--lumo-font-size-xl)")
        .set("font-weight", "bold")
        .set("color", "var(--lumo-primary-color)");
    totalCostDiv.add(totalCostLabel, totalCostValue);
    
    Div countDiv = new Div();
    H5 countLabel = new H5("Total Subscriptions");
    Span countValue = new Span(String.valueOf(totalCount));
    countValue.getStyle()
        .set("font-size", "var(--lumo-font-size-xl)")
        .set("font-weight", "bold");
    countDiv.add(countLabel, countValue);
    
    Div untrackedDiv = new Div();
    H5 untrackedLabel = new H5("Untracked Subscriptions");
    Span untrackedValue = new Span(String.valueOf(untrackedCount));
    untrackedValue.getStyle()
        .set("font-size", "var(--lumo-font-size-xl)")
        .set("font-weight", "bold")
        .set("color", untrackedCount > 0 ? "var(--lumo-error-color)" : "var(--lumo-success-color)");
    untrackedDiv.add(untrackedLabel, untrackedValue);
    
    stats.add(totalCostDiv, countDiv, untrackedDiv);
    summaryCard.add(summaryTitle, stats);
    
    return summaryCard;
}

private Div createSubscriptionCard(SubscriptionInfo subscription) {
    Div card = new Div();
    card.getStyle()
        .set("background-color", "var(--lumo-base-color)")
        .set("border-radius", "var(--lumo-border-radius-m)")
        .set("padding", "var(--lumo-space-m)")
        .set("box-shadow", "var(--lumo-box-shadow-xs)")
        .set("width", "calc(33% - var(--lumo-space-m))")
        .set("min-width", "300px")
        .set("position", "relative");
    
    // Highlight untracked subscriptions
    if (!subscription.isUserRecorded) {
        card.getStyle()
            .set("border-left", "4px solid var(--lumo-error-color)")
            .set("background-color", "var(--lumo-error-color-10pct)");
    } else {
        card.getStyle()
            .set("border-left", "4px solid var(--lumo-success-color)");
    }
    
    H4 merchantName = new H4(subscription.merchant);
    merchantName.getStyle().set("margin-top", "0");
    
    Span amount = new Span(String.format("$%.2f", subscription.amount));
    amount.getStyle()
        .set("font-size", "var(--lumo-font-size-l)")
        .set("font-weight", "bold");
    
    Span frequency = new Span(formatFrequency(subscription.frequencyDays));
    frequency.getStyle().set("color", "var(--lumo-secondary-text-color)");
    
    // Next payment info
    Div nextPayment = new Div();
    if (subscription.nextDate != null) {
        String formattedDate = subscription.nextDate.format(
            DateTimeFormatter.ofPattern("MMM d, yyyy"));
        Span nextPaymentLabel = new Span("Next payment: ");
        Span nextPaymentDate = new Span(formattedDate);
        nextPaymentDate.getStyle().set("font-weight", "bold");
        
        // Calculate days until next payment
        long daysUntil = ChronoUnit.DAYS.between(LocalDateTime.now(), subscription.nextDate);
        
        Span daysUntilSpan = new Span(String.format(" (%d days)", daysUntil));
        daysUntilSpan.getStyle().set("color", "var(--lumo-secondary-text-color)");
        
        nextPayment.add(nextPaymentLabel, nextPaymentDate, daysUntilSpan);
    } else {
        nextPayment.add(new Span("Next payment date unknown"));
    }
    
    // Calculate estimated monthly cost
    double monthlyMultiplier = 30.4 / subscription.frequencyDays;
    BigDecimal monthlyCost = subscription.amount.multiply(BigDecimal.valueOf(monthlyMultiplier));
    
    Span monthlyCostLabel = new Span("Est. monthly cost: ");
    Span monthlyCostValue = new Span(String.format("$%.2f", monthlyCost));
    monthlyCostValue.getStyle().set("font-weight", "bold");
    
    Div monthlyCostDiv = new Div(monthlyCostLabel, monthlyCostValue);
    
    // Action button
    HorizontalLayout actions = new HorizontalLayout();
    actions.setWidthFull();
    actions.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
    
    if (!subscription.isUserRecorded) {
        Button trackButton = new Button("Track Subscription", e -> {
            Dialog dialog = new Dialog();
            dialog.setHeaderTitle("Track Recurring Subscription");
            
            // Create form layout
            VerticalLayout formLayout = new VerticalLayout();
            TextField descriptionField = new TextField("Description");
            descriptionField.setValue(subscription.merchant);
            descriptionField.setWidthFull();
            
            NumberField amountField = new NumberField("Amount");
            amountField.setValue(subscription.amount.doubleValue());
            amountField.setMin(0);
            amountField.setWidthFull();
            
            ComboBox<RecurringTransaction.RecurrencePattern> patternField = 
                new ComboBox<>("Frequency");
            patternField.setItems(RecurringTransaction.RecurrencePattern.values());
            patternField.setWidthFull();
            
            // Set appropriate pattern based on frequencyDays
            RecurringTransaction.RecurrencePattern suggestedPattern;
            if (subscription.frequencyDays <= 1.5) {
                suggestedPattern = RecurringTransaction.RecurrencePattern.DAILY;
            } else if (subscription.frequencyDays <= 10) {
                suggestedPattern = RecurringTransaction.RecurrencePattern.WEEKLY;
            } else if (subscription.frequencyDays <= 20) {
                suggestedPattern = RecurringTransaction.RecurrencePattern.BIWEEKLY;
            } else if (subscription.frequencyDays <= 40) {
                suggestedPattern = RecurringTransaction.RecurrencePattern.MONTHLY;
            } else {
                suggestedPattern = RecurringTransaction.RecurrencePattern.YEARLY;
            }
            patternField.setValue(suggestedPattern);
            
            // Select envelope
            ComboBox<Envelope> envelopeField = new ComboBox<>("Envelope");
            try {
                List<Envelope> envelopes = envelopeService.getAllEnvelopes();
                envelopeField.setItems(envelopes);
                envelopeField.setItemLabelGenerator(Envelope::getName);
                
                // Try to guess appropriate envelope from transactions
                currentAccount.getTransactions().stream()
                    .filter(tx -> tx.getDescription().equals(subscription.merchant))
                    .findFirst()
                    .ifPresent(tx -> envelopeField.setValue(tx.getEnvelope()));
            } catch (Exception ex) {
                log.warn("Could not load envelopes", ex);
            }
            envelopeField.setWidthFull();
            
            formLayout.add(descriptionField, amountField, patternField, envelopeField);
            dialog.add(formLayout);
            
            // Add action buttons
            Button saveButton = new Button("Save", ev -> {
                try {
                    if (envelopeField.getValue() == null) {
                        Notification.show("Please select an envelope");
                        return;
                    }

                    RecurringTransaction newRecurring = new RecurringTransaction(
                        descriptionField.getValue(),
                        BigDecimal.valueOf(amountField.getValue()),
                        envelopeField.getValue(),
                        currentAccount,
                        patternField.getValue(),
                        TransactionType.EXPENSE
                    );

                    recurringTransactionService.create(newRecurring, currentAccount.getId());
                    dialog.close();

                    // Show success notification with "Done" badge
                    Notification notification = new Notification();
                    notification.setPosition(Notification.Position.MIDDLE);
                    notification.setDuration(3000);

                    HorizontalLayout notificationContent = new HorizontalLayout();
                    notificationContent.setAlignItems(FlexComponent.Alignment.CENTER);

                    Span doneLabel = new Span("✓");
                    doneLabel.getStyle()
                        .set("background-color", "var(--lumo-success-color)")
                        .set("border-radius", "50%")
                        .set("color", "var(--lumo-base-color)")
                        .set("display", "flex")
                        .set("height", "24px")
                        .set("width", "24px")
                        .set("align-items", "center")
                        .set("justify-content", "center");

                    Span message = new Span("Subscription tracked successfully!");

                    notificationContent.add(doneLabel, message);
                    notification.add(notificationContent);
                    notification.open();

                    // Refresh only the subscription panel
                    refreshSubscriptions();
                } catch (Exception ex) {
                    log.error("Error saving recurring transaction", ex);
                    Notification.show("Error: " + ex.getMessage());
                }
            });
            saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

            Button cancelButton = new Button("Cancel", ev -> dialog.close());

            dialog.getFooter().add(cancelButton, saveButton);
            dialog.open();
        });
        trackButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        actions.add(trackButton);
    } else {
        Span trackedBadge = new Span("Tracked");
        trackedBadge.getStyle()
            .set("background-color", "var(--lumo-success-color)")
            .set("color", "var(--lumo-base-color)")
            .set("padding", "var(--lumo-space-xs) var(--lumo-space-s)")
            .set("border-radius", "var(--lumo-border-radius-m)")
            .set("font-size", "var(--lumo-font-size-xs)");
        
        Button editButton = new Button("Edit", e -> {
            if (subscription.recurringTransaction != null) {
                Dialog dialog = new Dialog();
                dialog.setHeaderTitle("Edit Recurring Transaction");
                
                // Create form layout with existing values
                // Implementation similar to the "Track" dialog but with fields pre-filled
                
                dialog.open();
            }
        });
        editButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        actions.add(trackedBadge, editButton);
    }
    
    // Add confidence indicator for ML-detected subscriptions
    if (!subscription.isUserRecorded) {
        double confidence = subscription.confidence;
        String confidenceText = String.format("%.0f%% confidence", confidence * 100);
        
        Span confidenceLabel = new Span(confidenceText);
        confidenceLabel.getStyle()
            .set("position", "absolute")
            .set("top", "var(--lumo-space-xs)")
            .set("right", "var(--lumo-space-xs)")
            .set("font-size", "var(--lumo-font-size-xs)")
            .set("padding", "var(--lumo-space-xs)")
            .set("background-color", "var(--lumo-contrast-10pct)")
            .set("border-radius", "var(--lumo-border-radius-s)");
        
        card.add(confidenceLabel);
    }
    
    card.add(merchantName, amount, frequency, nextPayment, monthlyCostDiv, actions);
    return card;
}

private String formatFrequency(double days) {
    if (days <= 1.5) {
        return "Daily";
    } else if (days <= 9) {
        return "Weekly";
    } else if (days <= 18) {
        return "Bi-weekly";
    } else if (days <= 40) {
        return "Monthly";
    } else if (days <= 90) {
        return "Quarterly";
    } else if (days <= 200) {
        return "Semi-annually";
    } else {
        return "Annually";
    }
}

// Helper record to store subscription information
private record SubscriptionInfo(
    String merchant,
    BigDecimal amount,
    double frequencyDays,
    LocalDateTime lastDate,
    LocalDateTime nextDate,
    double confidence,
    boolean isUserRecorded,
    RecurringTransaction recurringTransaction
) {}
    private void updateTransactionList(TransactionType type, String searchPattern) {
        List<Transaction> transactions = currentAccount.getTransactions();
        
        // Filter by type if specified
        if (type != null) {
            transactions = transactions.stream()
                .filter(tx -> tx.getType() == type)
                .collect(Collectors.toList());
        }

        // Apply search pattern if provided
        if (searchPattern != null && !searchPattern.isEmpty()) {
            try {
                // Try as regex pattern first
                Pattern pattern = Pattern.compile(searchPattern, Pattern.CASE_INSENSITIVE);
                transactions = transactions.stream()
                    .filter(tx -> 
                        pattern.matcher(tx.getDescription()).find() ||
                        pattern.matcher(tx.getEnvelope().getName()).find() ||
                        pattern.matcher(tx.getAmount().toString()).find() ||
                        pattern.matcher(tx.getType().toString()).find()
                    )
                    .collect(Collectors.toList());
            } catch (PatternSyntaxException e) {
                // If regex fails, fall back to simple contains search
                String search = searchPattern.toLowerCase();
                transactions = transactions.stream()
                    .filter(tx ->
                        tx.getDescription().toLowerCase().contains(search) ||
                        tx.getEnvelope().getName().toLowerCase().contains(search) ||
                        tx.getAmount().toString().contains(search) ||
                        tx.getType().toString().toLowerCase().contains(search)
                    )
                    .collect(Collectors.toList());
            }
        }

        transactionGrid.setItems(transactions);
    }

    private void refreshData() {
        currentAccount = accountService.getAccount(currentAccount.getId());
        balanceChart.refreshData();
        updateTransactionList(null,"");
    }
}