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
package org.emblow.envelopify.ui.components;

/**
 *
 * @author Nicholas J Emblow
 */
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.grid.Grid;
import org.emblow.envelopify.domain.RecurringTransaction;
import org.emblow.envelopify.domain.BillReminder;
import org.emblow.envelopify.domain.Envelope;
import org.emblow.envelopify.service.RecurringTransactionService;
import org.emblow.envelopify.service.BillReminderService;
import org.emblow.envelopify.service.EnvelopeService;
import org.emblow.envelopify.service.AccountService;
import com.vaadin.flow.component.grid.contextmenu.GridContextMenu;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class EnvelopeSchedulePanel extends VerticalLayout {
    private final RecurringTransactionService recurringTransactionService;
    private final BillReminderService billReminderService;
    private final EnvelopeService envelopeService;
    private final AccountService accountService;
    private final Grid<RecurringTransaction> recurringGrid;
    private final Grid<BillReminder> billGrid;
    private final Envelope envelope;

    public EnvelopeSchedulePanel(
        Envelope envelope,
        RecurringTransactionService recurringTransactionService,
        BillReminderService billReminderService,
        EnvelopeService envelopeService,
        AccountService accountService
    ) {
        this.envelope = envelope;
        this.recurringTransactionService = recurringTransactionService;
        this.billReminderService = billReminderService;
        this.envelopeService = envelopeService;
        this.accountService = accountService;

        setPadding(true);
        setSpacing(true);

        // Recurring Transactions section
        H3 recurringTitle = new H3("Recurring Transactions");
        Button addRecurringButton = new Button(
            "Add Recurring",
            new Icon(VaadinIcon.PLUS),
            e -> showAddRecurringDialog()
        );
        addRecurringButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS);

        recurringGrid = new Grid<>();
        configureRecurringGrid();
        
        // Bills section
        H3 billsTitle = new H3("Bills");
        Button addBillButton = new Button(
            "Add Bill",
            new Icon(VaadinIcon.PLUS),
            e -> showAddBillDialog()
        );
        addBillButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS);

        billGrid = new Grid<>();
        configureBillGrid();

        add(
            recurringTitle,
            addRecurringButton,
            recurringGrid,
            billsTitle,
            addBillButton,
            billGrid
        );

        refreshData();
    }

    private void configureRecurringGrid() {
        recurringGrid.addColumn(RecurringTransaction::getDescription)
            .setHeader("Description")
            .setAutoWidth(true);
            
        recurringGrid.addColumn(tx -> 
            String.format("$%.2f", tx.getAmount()))
            .setHeader("Amount")
            .setAutoWidth(true);

        // Add account column
        recurringGrid.addColumn(tx -> 
            String.format("%s (%s)", 
                tx.getAccount().getName(),
                tx.getAccount().getType().getDisplayName()))
            .setHeader("Account")
            .setAutoWidth(true);
            
        recurringGrid.addColumn(tx -> tx.getPattern().toString())
            .setHeader("Pattern")
            .setAutoWidth(true);
            
        recurringGrid.addColumn(tx -> 
            tx.getNextDueDate().format(DateTimeFormatter.ofPattern("MM/dd/yyyy")))
            .setHeader("Next Due")
            .setAutoWidth(true);

        // Add edit/delete context menu
        GridContextMenu<RecurringTransaction> menu = new GridContextMenu<>(recurringGrid);
        menu.addItem("Edit", event -> {
            if (event.getItem().isPresent()) {
                showEditRecurringDialog(event.getItem().get());
            }
        });
        menu.addItem("Delete", event -> {
            if (event.getItem().isPresent()) {
                recurringTransactionService.delete(event.getItem().get().getId());
                refreshData();
            }
        });

        recurringGrid.setWidthFull();
    }

    private void configureBillGrid() {
        billGrid.addColumn(BillReminder::getDescription)
            .setHeader("Description")
            .setAutoWidth(true);
            
        billGrid.addColumn(bill -> 
            String.format("$%.2f", bill.getAmount()))
            .setHeader("Amount")
            .setAutoWidth(true);

        // Add account column
        billGrid.addColumn(bill -> 
            String.format("%s (%s)", 
                bill.getAccount().getName(),
                bill.getAccount().getType().getDisplayName()))
            .setHeader("Account")
            .setAutoWidth(true);
            
        billGrid.addColumn(bill -> 
            bill.getDueDate().format(DateTimeFormatter.ofPattern("MM/dd/yyyy")))
            .setHeader("Due Date")
            .setAutoWidth(true);
            
        billGrid.addColumn(BillReminder::isPaid)
            .setHeader("Paid")
            .setAutoWidth(true);

        // Add status color
        billGrid.setClassNameGenerator(bill -> {
            if (bill.isOverdue()) return "overdue";
            if (bill.needsReminder()) return "upcoming";
            if (bill.isPaid()) return "paid";
            return null;
        });

        // Add edit/delete context menu
        GridContextMenu<BillReminder> menu = new GridContextMenu<>(billGrid);
        menu.addItem("Edit", event -> {
            if (event.getItem().isPresent()) {
                showEditBillDialog(event.getItem().get());
            }
        });
        menu.addItem("Mark Paid", event -> {
            if (event.getItem().isPresent()) {
                billReminderService.markAsPaid(event.getItem().get().getId());
                refreshData();
            }
        });
        menu.addItem("Delete", event -> {
            if (event.getItem().isPresent()) {
                billReminderService.delete(event.getItem().get().getId());
                refreshData();
            }
        });

        billGrid.setWidthFull();
    }

    private void showAddRecurringDialog() {
        new RecurringTransactionDialog(
            recurringTransactionService,
            envelopeService,
            accountService,
            this::refreshData
        ).open();
    }

    private void showEditRecurringDialog(RecurringTransaction transaction) {
        new RecurringTransactionDialog(
            recurringTransactionService,
            envelopeService,
            accountService,
            this::refreshData,
            transaction
        ).open();
    }

    private void showAddBillDialog() {
        new BillReminderDialog(
            billReminderService,
            envelopeService,
            accountService,
            this::refreshData
        ).open();
    }

    private void showEditBillDialog(BillReminder bill) {
        new BillReminderDialog(
            billReminderService,
            envelopeService,
            accountService,
            this::refreshData,
            bill
        ).open();
    }

    private void refreshData() {
        List<RecurringTransaction> recurringTransactions = 
            recurringTransactionService.getAllForEnvelope(envelope.getId());
        recurringGrid.setItems(recurringTransactions);

        List<BillReminder> bills = 
            billReminderService.getAllForEnvelope(envelope.getId());
        billGrid.setItems(bills);
    }
}