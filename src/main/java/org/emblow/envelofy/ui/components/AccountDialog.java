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

/**
 *
 * @author Nicholas J Emblow
 */
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.validator.RegexpValidator;
import org.emblow.envelofy.domain.Account;
import org.emblow.envelofy.exception.AccountException;
import org.emblow.envelofy.exception.ValidationException;
import org.emblow.envelofy.service.AccountService;

import java.util.Map;

public class AccountDialog extends Dialog {
    private final AccountService accountService;
    private final Runnable onSuccess;
    private final Account existingAccount;
    private final Binder<Account> binder;

    public AccountDialog(AccountService accountService, Runnable onSuccess) {
        this(accountService, onSuccess, null);
    }

    public AccountDialog(AccountService accountService, Runnable onSuccess, Account existingAccount) {
        this.accountService = accountService;
        this.onSuccess = onSuccess;
        this.existingAccount = existingAccount;
        this.binder = new Binder<>(Account.class);

        setModal(true);
        setDraggable(true);

        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);
        layout.setSpacing(true);
        layout.setAlignItems(Alignment.STRETCH);

        H3 title = new H3(existingAccount == null ? "Add New Account" : "Edit Account");

        // Create form fields with validation
        TextField nameField = new TextField("Account Name");
        nameField.setRequired(true);
        nameField.setWidthFull();
        binder.forField(nameField)
            .asRequired("Name is required")
            .withValidator(name -> name.length() <= 100, "Name must be less than 100 characters")
            .bind(Account::getName, Account::setName);

        ComboBox<Account.AccountType> typeField = new ComboBox<>("Account Type");
        typeField.setItems(Account.AccountType.values());
        typeField.setItemLabelGenerator(Account.AccountType::getDisplayName);
        typeField.setRequired(true);
        binder.forField(typeField)
            .asRequired("Account type is required")
            .bind(Account::getType, Account::setType);

        TextField institutionField = new TextField("Financial Institution");
        institutionField.setWidthFull();
        binder.forField(institutionField)
            .bind(Account::getInstitution, Account::setInstitution);

        TextField accountNumberField = new TextField("Last 4 Digits");
        accountNumberField.setHelperText("For reference only");
        binder.forField(accountNumberField)
            .withValidator(new RegexpValidator(
                "Account number must be exactly 4 digits", "\\d{4}"))
            .bind(Account::getAccountNumber, Account::setAccountNumber);

        // Set existing values if editing
        if (existingAccount != null) {
            binder.readBean(existingAccount);
        }

        HorizontalLayout buttons = new HorizontalLayout();
        buttons.setJustifyContentMode(JustifyContentMode.END);
        buttons.setWidthFull();

        Button cancelButton = new Button("Cancel", e -> close());

        Button saveButton = new Button(existingAccount == null ? "Create" : "Save", new ComponentEventListener<ClickEvent<Button>>() {
            @Override
            public void onComponentEvent(ClickEvent<Button> e) {
                try {
                    Account account = existingAccount != null ?
                            existingAccount : new Account();
                    
                    // Validate and write field values to the account object
                    binder.writeBean(account);
                    
                    if (existingAccount == null) {
                        accountService.createAccount(
                                account.getName(),
                                account.getType(),
                                account.getInstitution(),
                                account.getAccountNumber()
                        );
                    } else {
                        accountService.save(account);
                    }
                    
                    close();
                    onSuccess.run();
                    showSuccess(existingAccount == null ? "Account created" : "Account updated");
                    
                } catch (AccountException ex) {
                    showError(ex.getMessage());
                } catch (ValidationException ex) {
                    handleValidationErrors((Map<String, String>) ex.getDetails().get("violations"));
                } catch (com.vaadin.flow.data.binder.ValidationException ex) {
                    showError("An unexpected error occurred: " + ex.getMessage());
                }
            }
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        buttons.add(cancelButton, saveButton);
        layout.add(title, nameField, typeField, institutionField, accountNumberField, buttons);
        add(layout);
    }

    private void showSuccess(String message) {
        Notification.show(message, 3000, Notification.Position.MIDDLE)
            .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private void showError(String message) {
        Notification.show(message, 5000, Notification.Position.MIDDLE)
            .addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    private void handleValidationErrors(Map<String, String> violations) {
        if (violations == null) return;
        
        StringBuilder errorMessage = new StringBuilder("Please correct the following errors:\n");
        violations.forEach((field, error) -> 
            errorMessage.append("• ").append(error).append("\n"));
        
        showError(errorMessage.toString());
    }
}