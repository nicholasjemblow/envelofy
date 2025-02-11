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

/**
 *
 * @author Nicholas J Emblow
 */
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.emblow.envelopify.domain.Envelope;
import org.emblow.envelopify.domain.Account;
import org.emblow.envelopify.domain.Transaction;
import org.emblow.envelopify.service.EnvelopeService;
import org.emblow.envelopify.service.AccountService;
import org.emblow.envelopify.service.TransactionService;
import org.emblow.envelopify.service.PatternService;
import org.emblow.envelopify.ui.MainLayout;
import org.emblow.envelopify.ui.components.EnvelopeCard;
import org.emblow.envelopify.ui.components.AccountCard;
import org.emblow.envelopify.ui.components.EnvelopeManagementDialogs;
import org.emblow.envelopify.ui.components.AccountDialog;
import java.time.LocalDateTime;
import java.util.List;
import org.emblow.envelopify.ui.components.TransactionManagementDialogs;
import org.emblow.envelopify.service.CSVImportService;
import org.emblow.envelopify.ui.components.CSVImportDialog;
import org.emblow.envelopify.ui.components.SpendingTrendsChart;

@Route(value = "dashboard", layout = MainLayout.class)
@RouteAlias(value = "", layout = MainLayout.class)
@PageTitle("Dashboard | Envelopify")
@AnonymousAllowed
public class DashboardView extends VerticalLayout {
    
    private final EnvelopeService envelopeService;
    private final AccountService accountService;
    private final TransactionService transactionService;
    private final PatternService patternService;
    private final CSVImportService csvImportService;  
    private final Grid<Transaction> transactionGrid = new Grid<>(Transaction.class);
    private final VerticalLayout envelopesLayout = new VerticalLayout();
    private final VerticalLayout accountsLayout = new VerticalLayout();
    private final EnvelopeManagementDialogs envelopeDialogs;
    private final TransactionManagementDialogs txDialogs;
    private final SpendingTrendsChart trendsChart; 

    public DashboardView(
        EnvelopeService envelopeService,
        AccountService accountService,
        TransactionService transactionService,
        PatternService patternService,
        CSVImportService csvImportService  
    ) {
        this.envelopeService = envelopeService;
        this.accountService = accountService;
        this.transactionService = transactionService;
        this.patternService = patternService;
        this.csvImportService = csvImportService;  
        this.envelopeDialogs = new EnvelopeManagementDialogs(envelopeService);
        this.txDialogs = new TransactionManagementDialogs(
            transactionService, 
            envelopeService,
            accountService,
            patternService
        );
        
        addClassName("dashboard-view");
        setPadding(true);
        setSpacing(true);

        add(createHeader());
        add(createAccountsSection());
        add(createEnvelopesSection());
        add(createRecentTransactionsSection());
        
       trendsChart = new SpendingTrendsChart(transactionService);
       add(trendsChart);  //chart doesnt work
        
        // Initial data load
        refreshData();
    }

    private HorizontalLayout createHeader() {
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setPadding(true);
        header.setAlignItems(Alignment.CENTER);
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);

        H2 title = new H2("Welcome to Envelopify");

        HorizontalLayout buttons = new HorizontalLayout();
        buttons.setSpacing(true);

        Button newTransactionButton = new Button(
            "New Transaction", 
            new Icon(VaadinIcon.PLUS_CIRCLE),
            e -> showNewTransactionDialog()
        );
        newTransactionButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button newEnvelopeButton = new Button(
            "New Envelope", 
            new Icon(VaadinIcon.FOLDER_O),
            e -> showNewEnvelopeDialog()
        );
        newEnvelopeButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS);

        Button newAccountButton = new Button(
            "New Account",
            new Icon(VaadinIcon.PIGGY_BANK),
            e -> showNewAccountDialog()
        );
        newAccountButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);

        Button importButton = new Button(
            "Import CSV",
            new Icon(VaadinIcon.UPLOAD),
            e -> showImportDialog()
        );
        importButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);

        buttons.add(importButton, newAccountButton, newTransactionButton, newEnvelopeButton);
        header.add(title, buttons);
        return header;
    }

    private VerticalLayout createAccountsSection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(true);
        section.setWidthFull();

        H3 title = new H3("Your Accounts");
        
        accountsLayout.setPadding(false);
        accountsLayout.setSpacing(true);
        accountsLayout.setWidthFull();
        
        section.add(title, accountsLayout);
        return section;
    }

    private VerticalLayout createEnvelopesSection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(true);
        section.setWidthFull();

        H3 title = new H3("Your Envelopes");
        
        envelopesLayout.setPadding(false);
        envelopesLayout.setSpacing(true);
        envelopesLayout.setWidthFull();
        
        section.add(title, envelopesLayout);
        return section;
    }

    private VerticalLayout createRecentTransactionsSection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(true);
        section.setWidthFull();

        H3 title = new H3("Recent Transactions");

        configureTransactionGrid();

        section.add(title, transactionGrid);
        return section;
    }

    private void configureTransactionGrid() {
        // Configure columns
        transactionGrid.setColumns("date", "description", "amount");
        transactionGrid.addColumn(tx -> tx.getAccount().getName())
            .setHeader("Account")
            .setKey("account");
        transactionGrid.addColumn(tx -> tx.getEnvelope().getName())
            .setHeader("Envelope")
            .setKey("envelope");
        transactionGrid.getColumns().forEach(col -> col.setAutoWidth(true));
        
        // Enable selection
        transactionGrid.setSelectionMode(Grid.SelectionMode.SINGLE);
        
        // Add double-click listener for editing
        transactionGrid.addItemDoubleClickListener(event -> {
            txDialogs.showEditTransactionDialog(event.getItem(), this::refreshData);
        });
        
        transactionGrid.setWidthFull();
    }

    private void showNewTransactionDialog() {
        txDialogs.showNewTransactionDialog(this::refreshData);
    }

    private void showNewEnvelopeDialog() {
        envelopeDialogs.showNewEnvelopeDialog(this::refreshData);
    }

    private void showNewAccountDialog() {
        new AccountDialog(accountService, this::refreshData).open();
    }

    private void showImportDialog() {
        new CSVImportDialog(csvImportService, envelopeService, accountService, this::refreshData).open();
    }

    private void refreshData() {
        try {
            // Load accounts
            List<Account> accounts = accountService.getAllAccounts();
            refreshAccounts(accounts);

            // Load envelopes
            List<Envelope> envelopes = envelopeService.getAllEnvelopes();
            refreshEnvelopes(envelopes);

            // Load recent transactions
            LocalDateTime oneMonthAgo = LocalDateTime.now().minusMonths(1);
            List<Transaction> recentTransactions = 
                transactionService.getRecentTransactions(oneMonthAgo, LocalDateTime.now());
            transactionGrid.setItems(recentTransactions);
        } catch (Exception e) {
            Notification.show(
                "Error loading dashboard data: " + e.getMessage(),
                3000,
                Notification.Position.MIDDLE
            );
        }
    }

    private void refreshAccounts(List<Account> accounts) {
        accountsLayout.removeAll();
        
        if (accounts.isEmpty()) {
            accountsLayout.add(
                new H3("No accounts yet. Add your first account to get started!")
            );
            return;
        }

        HorizontalLayout row = new HorizontalLayout();
        row.setWidthFull();
        row.setSpacing(true);
        
        for (Account account : accounts) {
            if (row.getComponentCount() >= 3) {
                accountsLayout.add(row);
                row = new HorizontalLayout();
                row.setWidthFull();
                row.setSpacing(true);
            }
            row.add(new AccountCard(account, accountService, this::refreshData));
        }
        
        if (row.getComponentCount() > 0) {
            accountsLayout.add(row);
        }
    }

    private void refreshEnvelopes(List<Envelope> envelopes) {
        envelopesLayout.removeAll();
        
        if (envelopes.isEmpty()) {
            envelopesLayout.add(
                new H3("No envelopes yet. Create your first envelope to get started!")
            );
            return;
        }

        HorizontalLayout row = new HorizontalLayout();
        row.setWidthFull();
        row.setSpacing(true);
        
        for (Envelope envelope : envelopes) {
            if (row.getComponentCount() >= 3) {
                envelopesLayout.add(row);
                row = new HorizontalLayout();
                row.setWidthFull();
                row.setSpacing(true);
            }
            row.add(new EnvelopeCard(envelope, envelopeDialogs));
        }
        
        if (row.getComponentCount() > 0) {
            envelopesLayout.add(row);
        }
    }
}