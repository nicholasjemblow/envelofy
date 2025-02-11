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
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.contextmenu.SubMenu;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import org.emblow.envelopify.domain.Account;
import org.emblow.envelopify.service.AccountService;

public class AccountCard extends Div {
    private final AccountService accountService;
    private final Runnable onModified;

    public AccountCard(Account account, AccountService accountService, Runnable onModified) {
        this.accountService = accountService;
        this.onModified = onModified;
        
        addClassName("account-card");
        getStyle()
            .set("background-color", "var(--lumo-base-color)")
            .set("border-radius", "var(--lumo-border-radius-l)")
            .set("box-shadow", "var(--lumo-box-shadow-s)")
            .set("padding", "var(--lumo-space-m)")
            .set("max-width", "300px")
            .set("width", "100%");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);

        // Header with name and type
        H4 name = new H4(account.getName());
        name.getStyle().setMargin("0");

        Span type = new Span(account.getType().getDisplayName());
        type.getStyle()
            .set("color", "var(--lumo-secondary-text-color)")
            .set("font-size", "var(--lumo-font-size-s)");

        // Balance information
        Span balance = new Span(String.format("Balance: $%.2f", account.getBalance()));
        balance.getStyle()
            .set("font-weight", "bold")
            .set("color", account.getBalance().signum() >= 0 ? 
                "var(--lumo-success-color)" : "var(--lumo-error-color)");

        // Quick actions
        HorizontalLayout actions = new HorizontalLayout();
        actions.setWidthFull();
        actions.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        Button viewButton = new Button(
            new Icon(VaadinIcon.LIST),
            e -> onViewClicked(account)
        );
        viewButton.getElement().setAttribute("title", "View transactions");

        MenuBar menuBar = new MenuBar();
        menuBar.getStyle()
            .set("min-width", "var(--lumo-button-size)")
            .set("min-height", "var(--lumo-button-size)");
        MenuItem menuItem = menuBar.addItem(new Icon(VaadinIcon.ELLIPSIS_DOTS_V));
        SubMenu subMenu = menuItem.getSubMenu();
        subMenu.addItem("Edit", e -> showEditDialog(account));
        subMenu.addItem("Delete", e -> showDeleteDialog(account));
        actions.add(viewButton, menuBar);

        // Institution info if available
        if (account.getInstitution() != null && !account.getInstitution().isEmpty()) {
            Span institution = new Span(account.getInstitution());
            institution.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)");
            content.add(name, type, institution, balance, actions);
        } else {
            content.add(name, type, balance, actions);
        }

        // Account number if available
        if (account.getAccountNumber() != null && !account.getAccountNumber().isEmpty()) {
            Span accountNumber = new Span("****" + account.getAccountNumber());
            accountNumber.getStyle()
                .set("color", "var(--lumo-tertiary-text-color)")
                .set("font-size", "var(--lumo-font-size-xs)");
            content.add(accountNumber);
        }

        add(content);
    }

    private void onViewClicked(Account account) {
        // TODO: Navigate to account details view when implemented
        // UI.getCurrent().navigate("accounts/" + account.getId());
        Notification.show("Account details view coming soon", 
            3000, Notification.Position.MIDDLE);
    }

    private void showEditDialog(Account account) {
        new AccountDialog(
            accountService,
            onModified,
            account
        ).open();
    }

    private void showDeleteDialog(Account account) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Delete Account");
        dialog.setText(
            "Are you sure you want to delete this account? " +
            "This will affect all transactions associated with it."
        );
        
        dialog.setCancelable(true);
        dialog.setConfirmText("Delete");
        dialog.setConfirmButtonTheme("error");
        
        dialog.addConfirmListener(event -> {
            try {
                accountService.deleteAccount(account.getId());
                onModified.run();
                Notification.show(
                    "Account deleted",
                    3000,
                    Notification.Position.MIDDLE
                );
            } catch (Exception ex) {
                Notification notification = Notification.show(
                    "Error deleting account: " + ex.getMessage(),
                    3000,
                    Notification.Position.MIDDLE
                );
                notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        
        dialog.open();
    }
}