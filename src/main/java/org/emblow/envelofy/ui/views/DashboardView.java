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

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.emblow.envelofy.domain.Account;
import org.emblow.envelofy.domain.Envelope;
import org.emblow.envelofy.domain.Transaction;
import org.emblow.envelofy.service.AccountService;
import org.emblow.envelofy.service.EnvelopeService;
import org.emblow.envelofy.service.TransactionService;
import org.emblow.envelofy.service.PatternService;
import org.emblow.envelofy.service.CSVImportService;
import org.emblow.envelofy.ui.components.EnvelopeCard;
import org.emblow.envelofy.ui.components.AccountCard;
import org.emblow.envelofy.ui.components.EnvelopeManagementDialogs;
import org.emblow.envelofy.ui.components.AccountDialog;
import org.emblow.envelofy.ui.components.TransactionManagementDialogs;
import org.emblow.envelofy.ui.components.CSVImportDialog;
import org.emblow.envelofy.ui.components.SpendingTrendsChart;
import java.time.LocalDateTime;
import java.util.List;
import org.emblow.envelofy.ui.MainLayout;

/**
 * DashboardView is the primary dashboard for an authenticated user.
 * <p>
 * This view includes multiple sections:
 * <ul>
 *   <li>A header with control buttons (new transaction, new envelope, new account, CSV import)</li>
 *   <li>An accounts section showing the current user's accounts</li>
 *   <li>An envelopes section showing the current user's envelopes</li>
 *   <li>A recent transactions section displaying a grid of transactions from the past month</li>
 *   <li>A spending trends chart based on recent transaction data</li>
 * </ul>
 * All data is obtained via service methods that filter by the current user.
 * </p>
 *
 * @author Nicholas J Emblow
 * @version 1.0
 * @since 2025
 */
@Route(value = "dashboard", layout = MainLayout.class)
@PageTitle("Dashboard | Envelofy")
@AnonymousAllowed
public class DashboardView extends VerticalLayout {

    private final EnvelopeService envelopeService;
    private final AccountService accountService;
    private final TransactionService transactionService;
    private final PatternService patternService;
    private final CSVImportService csvImportService;  
    private final TransactionManagementDialogs txDialogs;
    private final EnvelopeManagementDialogs envelopeDialogs;
    
    private final Grid<Transaction> transactionGrid = new Grid<>(Transaction.class);
    private final VerticalLayout envelopesLayout = new VerticalLayout();
    private final VerticalLayout accountsLayout = new VerticalLayout();
    private SpendingTrendsChart trendsChart; 

    /**
     * Constructs a new DashboardView and initializes all UI components.
     *
     * @param envelopeService   the service used for envelope operations (filtered by user)
     * @param accountService    the service used for account operations (filtered by user)
     * @param transactionService the service used for transaction operations (filtered by user)
     * @param patternService    the service used for pattern analysis on transactions
     * @param csvImportService  the service used to import transactions from CSV files
     * @param txDialogs         the dialogs used for creating and editing transactions
     */
    public DashboardView(EnvelopeService envelopeService,
                         AccountService accountService,
                         TransactionService transactionService,
                         PatternService patternService,
                         CSVImportService csvImportService,
                         TransactionManagementDialogs txDialogs) {
        this.envelopeService = envelopeService;
        this.accountService = accountService;
        this.transactionService = transactionService;
        this.patternService = patternService;
        this.csvImportService = csvImportService;
        this.txDialogs = txDialogs;
        this.envelopeDialogs = new EnvelopeManagementDialogs(envelopeService);
        
        addClassName("dashboard-view");
        setPadding(true);
        setSpacing(true);

        // Build and add the various sections of the dashboard.
        add(createHeader());
        add(createAccountsSection());
        add(createEnvelopesSection());
        add(createRecentTransactionsSection());
        
        // Create and add the spending trends chart.
        trendsChart = new SpendingTrendsChart(transactionService);
        add(trendsChart);
        
        // Load data for the current user.
        refreshData();
    }
    
    /**
     * Creates the header section which includes a title and control buttons.
     *
     * @return a HorizontalLayout containing header elements.
     */
    private HorizontalLayout createHeader() {
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setPadding(true);
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        H2 title = new H2("Welcome to Envelofy");

        // Create a layout to hold control buttons.
        HorizontalLayout buttons = new HorizontalLayout();
        buttons.setSpacing(true);

        // Button to launch the new transaction dialog.
        Button newTransactionButton = new Button("New Transaction", new Icon(VaadinIcon.PLUS_CIRCLE),
                e -> showNewTransactionDialog());
        newTransactionButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        // Button to launch the new envelope dialog.
        Button newEnvelopeButton = new Button("New Envelope", new Icon(VaadinIcon.FOLDER_O),
                e -> showNewEnvelopeDialog());
        newEnvelopeButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS);

        // Button to launch the new account dialog.
        Button newAccountButton = new Button("New Account", new Icon(VaadinIcon.PIGGY_BANK),
                e -> showNewAccountDialog());
        newAccountButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);

        // Button to open the CSV import dialog.
        Button importButton = new Button("Import CSV", new Icon(VaadinIcon.UPLOAD),
                e -> showImportDialog());
        importButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);

        buttons.add(importButton, newAccountButton, newTransactionButton, newEnvelopeButton);
        header.add(title, buttons);
        return header;
    }
    
    /**
     * Creates the accounts section of the dashboard.
     *
     * @return a VerticalLayout containing the accounts header and list.
     */
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
    
    /**
     * Creates the envelopes section of the dashboard.
     *
     * @return a VerticalLayout containing the envelopes header and list.
     */
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
    
    /**
     * Creates the recent transactions section which displays a grid of transactions.
     *
     * @return a VerticalLayout containing the transactions header and grid.
     */
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
    
    /**
     * Configures the transaction grid with necessary columns and sets up double-click editing.
     */
    private void configureTransactionGrid() {
        transactionGrid.setColumns("date", "description", "amount");
        transactionGrid.addColumn(tx -> tx.getAccount().getName())
                .setHeader("Account")
                .setKey("account");
        transactionGrid.addColumn(tx -> tx.getEnvelope().getName())
                .setHeader("Envelope")
                .setKey("envelope");
        transactionGrid.getColumns().forEach(col -> col.setAutoWidth(true));
        
        transactionGrid.setSelectionMode(Grid.SelectionMode.SINGLE);
        
        // Open the edit dialog when a transaction row is double-clicked.
        transactionGrid.addItemDoubleClickListener(event ->
            txDialogs.showEditTransactionDialog(event.getItem(), this::refreshData)
        );
    }
    
    /**
     * Opens the new transaction dialog.
     */
    private void showNewTransactionDialog() {
        txDialogs.showNewTransactionDialog(this::refreshData);
    }
    
    /**
     * Opens the new envelope dialog.
     */
    private void showNewEnvelopeDialog() {
        envelopeDialogs.showNewEnvelopeDialog(this::refreshData);
    }
    
    /**
     * Opens the new account dialog.
     */
    private void showNewAccountDialog() {
        new AccountDialog(accountService, this::refreshData).open();
    }
    
    /**
     * Opens the CSV import dialog.
     */
    private void showImportDialog() {
        new CSVImportDialog(csvImportService, envelopeService, accountService, this::refreshData).open();
    }
    
    /**
     * Refreshes the dashboard data by reloading accounts, envelopes, and recent transactions.
     * This method calls the service methods that return data only for the current user.
     */
    private void refreshData() {
        // Refresh accounts.
        List<Account> accounts = accountService.getAllAccounts();
        refreshAccounts(accounts);

        // Refresh envelopes.
        List<Envelope> envelopes = envelopeService.getAllEnvelopes();
        refreshEnvelopes(envelopes);

        // Refresh recent transactions for the past month.
        LocalDateTime oneMonthAgo = LocalDateTime.now().minusMonths(1);
        List<Transaction> recentTransactions = 
                transactionService.getRecentTransactions(oneMonthAgo, LocalDateTime.now());
        transactionGrid.setItems(recentTransactions);
    }
    
    /**
     * Updates the accounts layout with the given list of accounts.
     *
     * @param accounts the list of accounts belonging to the current user
     */
    private void refreshAccounts(List<Account> accounts) {
        accountsLayout.removeAll();
        
        if (accounts.isEmpty()) {
            accountsLayout.add(new H3("No accounts yet. Add your first account to get started!"));
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
    
    /**
     * Updates the envelopes layout with the given list of envelopes.
     *
     * @param envelopes the list of envelopes belonging to the current user
     */
    private void refreshEnvelopes(List<Envelope> envelopes) {
        envelopesLayout.removeAll();
        
        if (envelopes.isEmpty()) {
            envelopesLayout.add(new H3("No envelopes yet. Create your first envelope to get started!"));
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
