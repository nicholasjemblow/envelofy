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
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;
import org.emblow.envelopify.domain.RecurringTransaction;
import org.emblow.envelopify.domain.Envelope;
import org.emblow.envelopify.domain.Account;
import org.emblow.envelopify.service.RecurringTransactionService;
import org.emblow.envelopify.service.EnvelopeService;
import org.emblow.envelopify.service.AccountService;

import java.math.BigDecimal;

public class RecurringTransactionDialog extends Dialog {
    private final RecurringTransactionService recurringTransactionService;
    private final EnvelopeService envelopeService;
    private final AccountService accountService;
    private final Runnable onSuccess;
    private final RecurringTransaction existingTransaction;

    public RecurringTransactionDialog(
        RecurringTransactionService recurringTransactionService,
        EnvelopeService envelopeService,
        AccountService accountService,
        Runnable onSuccess
    ) {
        this(recurringTransactionService, envelopeService, accountService, onSuccess, null);
    }

    public RecurringTransactionDialog(
        RecurringTransactionService recurringTransactionService,
        EnvelopeService envelopeService,
        AccountService accountService,
        Runnable onSuccess,
        RecurringTransaction existingTransaction
    ) {
        this.recurringTransactionService = recurringTransactionService;
        this.envelopeService = envelopeService;
        this.accountService = accountService;
        this.onSuccess = onSuccess;
        this.existingTransaction = existingTransaction;

        setModal(true);
        setDraggable(true);

        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);
        layout.setSpacing(true);
        layout.setAlignItems(Alignment.STRETCH);

        H3 title = new H3(existingTransaction == null ? 
            "New Recurring Transaction" : "Edit Recurring Transaction");

        TextField descriptionField = new TextField("Description");
        descriptionField.setRequired(true);
        descriptionField.setWidthFull();

        BigDecimalField amountField = new BigDecimalField("Amount");
        amountField.setPrefixComponent(new Span("$"));
        amountField.setWidthFull();

        ComboBox<RecurringTransaction.RecurrencePattern> patternField = 
            new ComboBox<>("Recurrence Pattern");
        patternField.setItems(RecurringTransaction.RecurrencePattern.values());
        patternField.setRequired(true);

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

        ComboBox<Envelope> envelopeField = new ComboBox<>("Envelope");
        envelopeField.setItemLabelGenerator(Envelope::getName);
        envelopeField.setItems(envelopeService.getAllEnvelopes());
        envelopeField.setRequired(true);

        // Set existing values if editing
        if (existingTransaction != null) {
            descriptionField.setValue(existingTransaction.getDescription());
            amountField.setValue(existingTransaction.getAmount());
            patternField.setValue(existingTransaction.getPattern());
            accountField.setValue(existingTransaction.getAccount());
            envelopeField.setValue(existingTransaction.getEnvelope());
        }

        HorizontalLayout buttons = new HorizontalLayout();
        buttons.setJustifyContentMode(JustifyContentMode.END);
        buttons.setWidthFull();

        Button cancelButton = new Button("Cancel", e -> close());

        Button saveButton = new Button(
            existingTransaction == null ? "Create" : "Save", 
            e -> {
                try {
                    String description = descriptionField.getValue();
                    BigDecimal amount = amountField.getValue();
                    RecurringTransaction.RecurrencePattern pattern = patternField.getValue();
                    Account account = accountField.getValue();
                    Envelope envelope = envelopeField.getValue();

                    // Validation
                    if (description == null || description.trim().isEmpty()) {
                        throw new IllegalArgumentException("Please enter a description");
                    }
                    if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                        throw new IllegalArgumentException("Please enter a valid amount");
                    }
                    if (pattern == null) {
                        throw new IllegalArgumentException("Please select a recurrence pattern");
                    }
                    if (account == null) {
                        throw new IllegalArgumentException("Please select an account");
                    }
                    if (envelope == null) {
                        throw new IllegalArgumentException("Please select an envelope");
                    }

                    RecurringTransaction transaction = existingTransaction != null ? 
                        existingTransaction : new RecurringTransaction();
                    transaction.setDescription(description.trim());
                    transaction.setAmount(amount);
                    transaction.setPattern(pattern);
                    transaction.setAccount(account);
                    transaction.setEnvelope(envelope);

                    if (existingTransaction != null) {
                        recurringTransactionService.update(
                            existingTransaction.getId(), 
                            transaction, 
                            account.getId()
                        );
                    } else {
                        recurringTransactionService.create(transaction, account.getId());
                    }

                    close();
                    onSuccess.run();
                    
                    Notification.show(
                        existingTransaction == null ? 
                            "Recurring transaction created" : 
                            "Recurring transaction updated",
                        3000,
                        Notification.Position.MIDDLE
                    );

                } catch (Exception ex) {
                    Notification notification = Notification.show(
                        "Error: " + ex.getMessage(),
                        3000,
                        Notification.Position.MIDDLE
                    );
                    notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
                }
            }
        );
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        buttons.add(cancelButton, saveButton);
        layout.add(title, descriptionField, amountField, patternField, 
                  accountField, envelopeField, buttons);
        add(layout);
    }
}