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
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;
import org.emblow.envelopify.domain.BillReminder;
import org.emblow.envelopify.domain.Envelope;
import org.emblow.envelopify.domain.Account;
import org.emblow.envelopify.service.BillReminderService;
import org.emblow.envelopify.service.EnvelopeService;
import org.emblow.envelopify.service.AccountService;

import java.math.BigDecimal;
import java.time.LocalDate;

public class BillReminderDialog extends Dialog {
    private final BillReminderService billReminderService;
    private final EnvelopeService envelopeService;
    private final AccountService accountService;
    private final Runnable onSuccess;
    private final BillReminder existingBill;

    public BillReminderDialog(
        BillReminderService billReminderService,
        EnvelopeService envelopeService,
        AccountService accountService,
        Runnable onSuccess
    ) {
        this(billReminderService, envelopeService, accountService, onSuccess, null);
    }

    public BillReminderDialog(
        BillReminderService billReminderService,
        EnvelopeService envelopeService,
        AccountService accountService,
        Runnable onSuccess,
        BillReminder existingBill
    ) {
        this.billReminderService = billReminderService;
        this.envelopeService = envelopeService;
        this.accountService = accountService;
        this.onSuccess = onSuccess;
        this.existingBill = existingBill;

        setModal(true);
        setDraggable(true);

        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);
        layout.setSpacing(true);
        layout.setAlignItems(Alignment.STRETCH);

        H3 title = new H3(existingBill == null ? "New Bill Reminder" : "Edit Bill Reminder");

        TextField descriptionField = new TextField("Description");
        descriptionField.setRequired(true);
        descriptionField.setWidthFull();

        BigDecimalField amountField = new BigDecimalField("Amount");
        amountField.setPrefixComponent(new Span("$"));
        amountField.setWidthFull();

        DatePicker dueDateField = new DatePicker("Due Date");
        dueDateField.setRequired(true);
        dueDateField.setMin(LocalDate.now());

        IntegerField reminderDaysField = new IntegerField("Reminder Days Before");
        reminderDaysField.setValue(7);
        reminderDaysField.setMin(0);
        reminderDaysField.setMax(60);
        reminderDaysField.setStep(1);

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
        envelopeField.setItemLabelGenerator(env -> 
            String.format("%s - Available: $%.2f", 
                env.getName(),
                env.getAvailable()
            )
        );
        envelopeField.setItems(envelopeService.getAllEnvelopes());
        envelopeField.setRequired(true);

        // Set existing values if editing
        if (existingBill != null) {
            descriptionField.setValue(existingBill.getDescription());
            amountField.setValue(existingBill.getAmount());
            dueDateField.setValue(existingBill.getDueDate());
            reminderDaysField.setValue(existingBill.getReminderDays());
            accountField.setValue(existingBill.getAccount());
            envelopeField.setValue(existingBill.getEnvelope());
        }

        HorizontalLayout buttons = new HorizontalLayout();
        buttons.setJustifyContentMode(JustifyContentMode.END);
        buttons.setWidthFull();

        Button cancelButton = new Button("Cancel", e -> close());

        Button saveButton = new Button(existingBill == null ? "Create" : "Save", e -> {
            try {
                String description = descriptionField.getValue();
                BigDecimal amount = amountField.getValue();
                LocalDate dueDate = dueDateField.getValue();
                int reminderDays = reminderDaysField.getValue();
                Account account = accountField.getValue();
                Envelope envelope = envelopeField.getValue();

                // Validation
                if (description == null || description.trim().isEmpty()) {
                    throw new IllegalArgumentException("Please enter a description");
                }
                if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("Please enter a valid amount");
                }
                if (dueDate == null) {
                    throw new IllegalArgumentException("Please select a due date");
                }
                if (account == null) {
                    throw new IllegalArgumentException("Please select an account");
                }
                if (envelope == null) {
                    throw new IllegalArgumentException("Please select an envelope");
                }

                BillReminder reminder = existingBill != null ? existingBill : new BillReminder();
                reminder.setDescription(description.trim());
                reminder.setAmount(amount);
                reminder.setDueDate(dueDate);
                reminder.setReminderDays(reminderDays);
                reminder.setAccount(account);
                reminder.setEnvelope(envelope);
                reminder.setPaid(false);

                if (existingBill != null) {
                    billReminderService.update(existingBill.getId(), reminder);
                } else {
                    billReminderService.create(reminder);
                }

                close();
                onSuccess.run();
                
                Notification.show(
                    existingBill == null ? "Bill reminder created" : "Bill reminder updated",
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
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        buttons.add(cancelButton, saveButton);
        layout.add(title, descriptionField, amountField, dueDateField, 
                  reminderDaysField, accountField, envelopeField, buttons);
        add(layout);
    }
}