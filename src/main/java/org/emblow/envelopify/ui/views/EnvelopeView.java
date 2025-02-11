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
package org.emblow.envelopify.ui.views;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.grid.GridSortOrder;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.data.provider.SortDirection;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.emblow.envelopify.domain.Envelope;
import org.emblow.envelopify.domain.Transaction;
import org.emblow.envelopify.domain.TransactionType;
import org.emblow.envelopify.domain.Account;
import org.emblow.envelopify.service.EnvelopeService;
import org.emblow.envelopify.service.TransactionService;
import org.emblow.envelopify.service.AccountService;
import org.emblow.envelopify.service.RecurringTransactionService;
import org.emblow.envelopify.service.BillReminderService;
import org.emblow.envelopify.ui.MainLayout;
import org.emblow.envelopify.ui.components.EnvelopeSchedulePanel;
import org.emblow.envelopify.ui.components.TransactionManagementDialogs;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.math.BigDecimal;

@Route(value = "envelopes", layout = MainLayout.class)
@AnonymousAllowed
public class EnvelopeView extends VerticalLayout implements HasUrlParameter<Long> {
    
    private final EnvelopeService envelopeService;
    private final TransactionService transactionService;
    private final AccountService accountService;
    private final RecurringTransactionService recurringTransactionService;
    private final BillReminderService billReminderService;
    private final TransactionManagementDialogs transactionDialogs;
    
    private final VerticalLayout contentLayout = new VerticalLayout();
    private Envelope currentEnvelope;
    private final Grid<Transaction> transactionGrid;

    public EnvelopeView(
        EnvelopeService envelopeService,
        TransactionService transactionService,
        AccountService accountService,
        RecurringTransactionService recurringTransactionService,
        BillReminderService billReminderService,
        TransactionManagementDialogs transactionDialogs
    ) {
        this.envelopeService = envelopeService;
        this.transactionService = transactionService;
        this.accountService = accountService;
        this.recurringTransactionService = recurringTransactionService;
        this.billReminderService = billReminderService;
        this.transactionDialogs = transactionDialogs;
        
        setPadding(true);
        setSpacing(true);
        
        // Initialize transaction grid
        transactionGrid = new Grid<>();
        configureTransactionGrid();
        
        // Initialize layout
        contentLayout.setPadding(false);
        contentLayout.setSpacing(true);
        
        add(contentLayout);
    }

    @Override
    public void setParameter(BeforeEvent event, Long envelopeId) {
        currentEnvelope = envelopeService.getEnvelope(envelopeId);
        updateView();
    }
    
    private void updateView() {
        contentLayout.removeAll();
        
        // Header section
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(Alignment.CENTER);
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);
        
        H2 title = new H2(currentEnvelope.getName());
        
        Button newTransactionButton = new Button(
            "New Transaction", 
            new Icon(VaadinIcon.PLUS),
            e -> transactionDialogs.showNewTransactionDialog(this::refreshData)
        );
        newTransactionButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        
        header.add(title, newTransactionButton);
        
        // Overview Panel
        Div overviewPanel = createOverviewPanel();
        
        // Tabs for different sections
        Tab overviewTab = new Tab("Overview");
        Tab scheduleTab = new Tab("Schedule");
        Tab historyTab = new Tab("History");
        Tab analyticsTab = new Tab("Analytics");
        
        Tabs tabs = new Tabs(overviewTab, scheduleTab, historyTab, analyticsTab);
        tabs.addSelectedChangeListener(event -> {
            updateContent(event.getSelectedTab());
        });
        
        contentLayout.add(header, overviewPanel, tabs);
        
        // Show initial content
        updateContent(overviewTab);
    }
    
    private Div createOverviewPanel() {
        Div panel = new Div();
        panel.addClassName("overview-panel");
        panel.getStyle()
            .set("background-color", "var(--lumo-base-color)")
            .set("border-radius", "var(--lumo-border-radius-l)")
            .set("padding", "var(--lumo-space-m)")
            .set("margin-bottom", "var(--lumo-space-m)");
        
        // General stats
        H3 statsTitle = new H3("Envelope Statistics");
        Div statsGrid = new Div();
        statsGrid.getStyle()
            .set("display", "grid")
            .set("grid-template-columns", "repeat(auto-fit, minmax(200px, 1fr))")
            .set("gap", "var(--lumo-space-m)");
        
        addStatCard(statsGrid, "Monthly Budget", 
            String.format("$%.2f", currentEnvelope.getMonthlyBudget()));
        addStatCard(statsGrid, "Available", 
            String.format("$%.2f", currentEnvelope.getAvailable()));
        addStatCard(statsGrid, "Spent This Month", 
            String.format("$%.2f", currentEnvelope.getCurrentMonthSpent()));
        
        // Account breakdown
        H3 accountsTitle = new H3("Account Breakdown");
        Div accountsGrid = new Div();
        accountsGrid.getStyle()
            .set("display", "grid")
            .set("grid-template-columns", "repeat(auto-fit, minmax(250px, 1fr))")
            .set("gap", "var(--lumo-space-m)");
        
        // Calculate spending by account
        Map<Account, BigDecimal> spendingByAccount = calculateSpendingByAccount();
        
        spendingByAccount.forEach((account, amount) -> {
            addAccountCard(accountsGrid, account, amount);
        });
        
        panel.add(statsTitle, statsGrid, accountsTitle, accountsGrid);
        return panel;
    }
    
    private void addStatCard(Div container, String label, String value) {
        Div card = new Div();
        card.getStyle()
            .set("background-color", "var(--lumo-contrast-5pct)")
            .set("padding", "var(--lumo-space-m)")
            .set("border-radius", "var(--lumo-border-radius-m)")
            .set("text-align", "center");
        
        Div labelDiv = new Div();
        labelDiv.setText(label);
        labelDiv.getStyle().set("color", "var(--lumo-secondary-text-color)");
        
        Div valueDiv = new Div();
        valueDiv.setText(value);
        valueDiv.getStyle()
            .set("font-size", "var(--lumo-font-size-xl)")
            .set("font-weight", "bold");
        
        card.add(labelDiv, valueDiv);
        container.add(card);
    }
    
    private void addAccountCard(Div container, Account account, BigDecimal amount) {
        Div card = new Div();
        card.getStyle()
            .set("background-color", "var(--lumo-contrast-5pct)")
            .set("padding", "var(--lumo-space-m)")
            .set("border-radius", "var(--lumo-border-radius-m)");
        
        H3 accountName = new H3(account.getName());
        accountName.getStyle()
            .set("margin", "0")
            .set("margin-bottom", "var(--lumo-space-s)");
        
        Div typeLabel = new Div();
        typeLabel.setText(account.getType().getDisplayName());
        typeLabel.getStyle()
            .set("color", "var(--lumo-secondary-text-color)")
            .set("margin-bottom", "var(--lumo-space-s)");
        
        Div amountLabel = new Div();
        amountLabel.setText(String.format("Spent: $%.2f", amount));
        amountLabel.getStyle()
            .set("font-weight", "bold")
            .set("color", "var(--lumo-primary-text-color)");
        
        card.add(accountName, typeLabel, amountLabel);
        container.add(card);
    }
    
    private Map<Account, BigDecimal> calculateSpendingByAccount() {
        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0);
        List<Transaction> transactions = transactionService.getRecentTransactions(
            monthStart, 
            LocalDateTime.now()
        );
        
        return transactions.stream()
            .filter(tx -> tx.getEnvelope().getId().equals(currentEnvelope.getId()))
            .collect(Collectors.groupingBy(
                Transaction::getAccount,
                Collectors.reducing(
                    BigDecimal.ZERO,
                    Transaction::getAmount,
                    BigDecimal::add
                )
            ));
    }
    
    private void configureTransactionGrid() {
        transactionGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        
        transactionGrid.addColumn(tx -> 
            tx.getDate().format(DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm")))
            .setHeader("Date")
            .setSortable(true)
            .setAutoWidth(true);
            
        transactionGrid.addColumn(Transaction::getDescription)
            .setHeader("Description")
            .setAutoWidth(true);
            
        transactionGrid.addColumn(tx -> 
            String.format("$%.2f", tx.getAmount()))
            .setHeader("Amount")
            .setAutoWidth(true);
        
        // NEW: Add a column to show the transaction type (Income/Expense)
        transactionGrid.addColumn(tx -> 
            tx.getType().toString())
            .setHeader("Type")
            .setAutoWidth(true);
            
        transactionGrid.addColumn(tx -> 
            String.format("%s (%s)", 
                tx.getAccount().getName(),
                tx.getAccount().getType().getDisplayName()))
            .setHeader("Account")
            .setAutoWidth(true);
            
        // Add double-click listener for editing
        transactionGrid.addItemDoubleClickListener(event -> {
            transactionDialogs.showEditTransactionDialog(event.getItem(), this::refreshData);
        });
        
        // Set default sort on the Date column
        GridSortOrder<Transaction> order = new GridSortOrder<>(
            transactionGrid.getColumns().get(0), 
            SortDirection.DESCENDING
        );
        transactionGrid.sort(List.of(order));
    }
    
    private void updateContent(Tab selectedTab) {
        // Remove old content (after header and tabs)
        while (contentLayout.getComponentCount() > 3) {
            contentLayout.remove(contentLayout.getComponentAt(3));
        }
        
        if (selectedTab.getLabel().equals("Overview")) {
            contentLayout.add(createOverviewContent());
        } else if (selectedTab.getLabel().equals("Schedule")) {
            contentLayout.add(new EnvelopeSchedulePanel(
                currentEnvelope,
                recurringTransactionService,
                billReminderService,
                envelopeService,
                accountService
            ));
        } else if (selectedTab.getLabel().equals("History")) {
            contentLayout.add(createHistoryContent());
        } else if (selectedTab.getLabel().equals("Analytics")) {
            contentLayout.add(createAnalyticsContent());
        }
    }
    
    private VerticalLayout createOverviewContent() {
        VerticalLayout layout = new VerticalLayout();
        layout.setSpacing(true);
        layout.setPadding(false);
        
        // Add recent transactions
        H3 recentTitle = new H3("Recent Transactions");
        layout.add(recentTitle, transactionGrid);
        
        refreshData();
        return layout;
    }
    
    private VerticalLayout createHistoryContent() {
        VerticalLayout layout = new VerticalLayout();
        layout.add(transactionGrid);
        refreshData();
        return layout;
    }
    
    private VerticalLayout createAnalyticsContent() {
        VerticalLayout layout = new VerticalLayout();
        // Add spending trends, patterns, etc.
        return layout;
    }
    
    private void refreshData() {
        LocalDateTime oneMonthAgo = LocalDateTime.now().minusMonths(1);
        List<Transaction> transactions = transactionService.getRecentTransactions(
            oneMonthAgo,
            LocalDateTime.now()
        ).stream()
        .filter(tx -> tx.getEnvelope().getId().equals(currentEnvelope.getId()))
        .collect(Collectors.toList());
        
        transactionGrid.setItems(transactions);
    }
}
