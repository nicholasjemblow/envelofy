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
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;
import org.emblow.envelopify.domain.Account;
import org.emblow.envelopify.service.AccountService;

public class AccountDialog extends Dialog {
    private final AccountService accountService;
    private final Runnable onSuccess;
    private final Account existingAccount;

    public AccountDialog(AccountService accountService, Runnable onSuccess) {
        this(accountService, onSuccess, null);
    }

    public AccountDialog(
        AccountService accountService,
        Runnable onSuccess,
        Account existingAccount
    ) {
        this.accountService = accountService;
        this.onSuccess = onSuccess;
        this.existingAccount = existingAccount;

        setModal(true);
        setDraggable(true);

        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);
        layout.setSpacing(true);
        layout.setAlignItems(Alignment.STRETCH);

        H3 title = new H3(existingAccount == null ? 
            "Add New Account" : "Edit Account");

        TextField nameField = new TextField("Account Name");
        nameField.setRequired(true);
        nameField.setWidthFull();

        ComboBox<Account.AccountType> typeField = new ComboBox<>("Account Type");
        typeField.setItems(Account.AccountType.values());
        typeField.setItemLabelGenerator(Account.AccountType::getDisplayName);
        typeField.setRequired(true);

        TextField institutionField = new TextField("Financial Institution");
        institutionField.setWidthFull();

        TextField accountNumberField = new TextField("Last 4 Digits");
        accountNumberField.setHelperText("For reference only");
        accountNumberField.setPattern("[0-9]{4}");
        accountNumberField.setMaxLength(4);

        // Set existing values if editing
        if (existingAccount != null) {
            nameField.setValue(existingAccount.getName());
            typeField.setValue(existingAccount.getType());
            institutionField.setValue(existingAccount.getInstitution());
            if (existingAccount.getAccountNumber() != null) {
                accountNumberField.setValue(existingAccount.getAccountNumber());
            }
        }

        HorizontalLayout buttons = new HorizontalLayout();
        buttons.setJustifyContentMode(JustifyContentMode.END);
        buttons.setWidthFull();

        Button cancelButton = new Button("Cancel", e -> close());

        Button saveButton = new Button(
            existingAccount == null ? "Create" : "Save",
            e -> {
                try {
                    String name = nameField.getValue();
                    Account.AccountType type = typeField.getValue();
                    String institution = institutionField.getValue();
                    String accountNumber = accountNumberField.getValue();

                    if (name == null || name.trim().isEmpty()) {
                        throw new IllegalArgumentException("Please enter an account name");
                    }
                    if (type == null) {
                        throw new IllegalArgumentException("Please select an account type");
                    }

                    if (existingAccount == null) {
                        accountService.createAccount(
                            name.trim(),
                            type,
                            institution,
                            accountNumber
                        );
                    } else {
                        existingAccount.setName(name.trim());
                        existingAccount.setType(type);
                        existingAccount.setInstitution(institution);
                        existingAccount.setAccountNumber(accountNumber);
                        // Save changes through service
                        accountService.save(existingAccount);
                    }

                    close();
                    onSuccess.run();
                    
                    Notification.show(
                        existingAccount == null ? 
                            "Account created" : "Account updated",
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
        layout.add(title, nameField, typeField, institutionField, 
                  accountNumberField, buttons);
        add(layout);
    }
}