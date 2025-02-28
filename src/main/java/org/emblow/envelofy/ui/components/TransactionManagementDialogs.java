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
package org.emblow.envelofy.ui.components;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.timepicker.TimePicker;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import org.emblow.envelofy.domain.Envelope;
import org.emblow.envelofy.domain.Account;
import org.emblow.envelofy.domain.Transaction;
import org.emblow.envelofy.domain.TransactionType;
import org.emblow.envelofy.service.TransactionService;
import org.emblow.envelofy.service.EnvelopeService;
import org.emblow.envelofy.service.AccountService;
import org.emblow.envelofy.service.PatternService;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@SpringComponent
@UIScope
public class TransactionManagementDialogs {

    private final TransactionService transactionService;
    private final EnvelopeService envelopeService;
    private final AccountService accountService;
    private final PatternService patternService;

    public TransactionManagementDialogs(
        TransactionService transactionService,
        EnvelopeService envelopeService,
        AccountService accountService,
        PatternService patternService
    ) {
        this.transactionService = transactionService;
        this.envelopeService = envelopeService;
        this.accountService = accountService;
        this.patternService = patternService;
    }

    public void showNewTransactionDialog(Runnable onSuccess) {
        // Create a fully initialized transaction with default values
        Transaction newTransaction = new Transaction();
        newTransaction.setDate(LocalDateTime.now());
        newTransaction.setDescription("");
        newTransaction.setAmount(BigDecimal.ZERO);
        newTransaction.setType(TransactionType.EXPENSE);
        showTransactionDialog(newTransaction, onSuccess);
    }

    public void showEditTransactionDialog(Transaction transaction, Runnable onSuccess) {
        showTransactionDialog(transaction, onSuccess);
    }
    public void showNewTransactionDialog(Runnable onSuccess, Account preselectedAccount) {
        // Create a new (empty) transaction and preselect the account.
        Transaction newTransaction = new Transaction();
        newTransaction.setAccount(preselectedAccount);
        showTransactionDialog(newTransaction, onSuccess);
    }

    private void showTransactionDialog(Transaction transaction, Runnable onSuccess) {
        boolean isEdit = transaction != null;
        
        Dialog dialog = new Dialog();
        dialog.setModal(true);
        dialog.setDraggable(true);

        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);
        layout.setSpacing(true);
        layout.setAlignItems(Alignment.STRETCH);

        H3 title = new H3(isEdit ? "Edit Transaction" : "New Transaction");

        // Date and time pickers
        DatePicker datePicker = new DatePicker("Date");
        TimePicker timePicker = new TimePicker("Time");
        
        if (isEdit) {
            datePicker.setValue(transaction.getDate().toLocalDate());
            timePicker.setValue(transaction.getDate().toLocalTime());
        } else {
            datePicker.setValue(java.time.LocalDate.now());
            timePicker.setValue(java.time.LocalTime.now());
        }

        // Description field
        TextField descriptionField = new TextField("Description");
        descriptionField.setWidthFull();
        descriptionField.setRequired(true);
        if (isEdit) {
            descriptionField.setValue(transaction.getDescription());
        }

        // Amount field
        BigDecimalField amountField = new BigDecimalField("Amount");
        amountField.setPrefixComponent(new Span("$"));
        amountField.setWidthFull();
        if (isEdit) {
            amountField.setValue(transaction.getAmount());
        } else {
            amountField.setValue(BigDecimal.ZERO);
        }

        // Transaction Type selector
        ComboBox<TransactionType> typeField = new ComboBox<>("Transaction Type");
        typeField.setItems(TransactionType.values());
        typeField.setItemLabelGenerator(tt -> tt == TransactionType.INCOME ? "Income" : "Expense");
        typeField.setWidthFull();
        typeField.setRequired(true);
        if (isEdit) {
            typeField.setValue(transaction.getType());
        } else {
            // Default to Expense for new transactions
            typeField.setValue(TransactionType.EXPENSE);
        }

        // Account selector
        ComboBox<Account> accountField = new ComboBox<>("Account");
        accountField.setItemLabelGenerator(account -> 
            String.format("%s (%s) - Balance: $%.2f", 
                account.getName(),
                account.getType().getDisplayName(),
                account.getBalance()
            )
        );
        accountField.setItems(accountService.getAllAccounts());
        accountField.setRequired(true);
        accountField.setWidthFull();
        if (isEdit || transaction != null && transaction.getAccount() != null) {
            accountField.setValue(transaction.getAccount());
        }


        // Envelope selector
        ComboBox<Envelope> envelopeField = new ComboBox<>("Envelope");
        envelopeField.setItemLabelGenerator(env -> 
            String.format("%s - Available: $%.2f", 
                env.getName(),
                env.getAvailable()
            )
        );
        envelopeField.setItems(envelopeService.getAllEnvelopes());
        envelopeField.setWidthFull();
        envelopeField.setRequired(true);
        if (isEdit) {
            envelopeField.setValue(transaction.getEnvelope());
        }

        // Add envelope suggestions
        EnvelopeSuggestions suggestions = new EnvelopeSuggestions(envelopeField, patternService);

        // Update suggestions when description changes
        descriptionField.addValueChangeListener(e -> {
            Transaction temp = new Transaction();
            temp.setDescription(e.getValue());
            temp.setAmount(amountField.getValue());
            temp.setDate(LocalDateTime.of(datePicker.getValue(), timePicker.getValue()));
            temp.setAccount(accountField.getValue());
            suggestions.updateSuggestions(temp);
        });

        // Buttons
        HorizontalLayout buttons = new HorizontalLayout();
        buttons.setJustifyContentMode(JustifyContentMode.END);
        buttons.setWidthFull();

        Button cancelButton = new Button("Cancel", e -> dialog.close());
        
        Button saveButton = new Button(isEdit ? "Save" : "Create", new ComponentEventListener<ClickEvent<Button>>() {
            @Override
            public void onComponentEvent(ClickEvent<Button> e) {
                try {
                    String description = descriptionField.getValue();
                    BigDecimal amount = amountField.getValue();
                    TransactionType type = typeField.getValue();
                    Envelope envelope = envelopeField.getValue();
                    Account account = accountField.getValue();
                    LocalDateTime dateTime = LocalDateTime.of(
                            datePicker.getValue(),
                            timePicker.getValue()
                    );
                    
                    // Validation
                    if (description == null || description.trim().isEmpty()) {
                        throw new IllegalArgumentException("Please enter a description");
                    }
                    if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                        throw new IllegalArgumentException("Please enter a non-zero amount");
                    }
                    if (envelope == null) {
                        throw new IllegalArgumentException("Please select an envelope");
                    }
                    if (account == null) {
                        throw new IllegalArgumentException("Please select an account");
                    }
                    if (type == null) {
                        throw new IllegalArgumentException("Please select a transaction type");
                    }
                    
                if (isEdit && transaction.getId() != null) {
                    transactionService.updateTransaction(
                        transaction.getId(),
                        envelope.getId(),
                        account.getId(),
                        dateTime,
                        description.trim(),
                        amount,
                        type
                    );
                } else {
                    transactionService.recordTransaction(
                        envelope.getId(),
                        account.getId(),
                        dateTime,
                        description.trim(),
                        amount,
                        type
                    );
                }
                    
                    dialog.close();
                    onSuccess.run();
                    
                    Notification.show(
                            isEdit ? "Transaction updated" : "Transaction recorded",
                            3000,
                            Notification.Position.MIDDLE
                    );
                } catch (IllegalArgumentException ex) {
                    Notification notification = Notification.show(
                            "Error: " + ex.getMessage(),
                            3000,
                            Notification.Position.MIDDLE
                    );
                    notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
                }
            }
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button deleteButton = null;
        if (isEdit) {
            deleteButton = new Button("Delete", e -> {
                ConfirmDialog confirmDialog = new ConfirmDialog();
                confirmDialog.setHeader("Delete Transaction");
                confirmDialog.setText("Are you sure you want to delete this transaction? This cannot be undone.");
                
                confirmDialog.setCancelable(true);
                confirmDialog.setConfirmText("Delete");
                confirmDialog.setConfirmButtonTheme("error");
                
                confirmDialog.addConfirmListener(event -> {
                    try {
                        transactionService.deleteTransaction(transaction.getId());
                        dialog.close();
                        onSuccess.run();
                        Notification.show("Transaction deleted", 3000, Notification.Position.MIDDLE);
                    } catch (Exception ex) {
                        Notification.show(
                            "Error deleting transaction: " + ex.getMessage(),
                            3000,
                            Notification.Position.MIDDLE
                        );
                    }
                });
                
                confirmDialog.open();
            });
            deleteButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
            buttons.add(deleteButton);
        }

        buttons.add(cancelButton, saveButton);

        layout.add(
            title,
            new HorizontalLayout(datePicker, timePicker),
            accountField,
            descriptionField,
            amountField,
            typeField,
            envelopeField,
            suggestions,
            buttons
        );

        dialog.add(layout);
        dialog.open();
    }
}
