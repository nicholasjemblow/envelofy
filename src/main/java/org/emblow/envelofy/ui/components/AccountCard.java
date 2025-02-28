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
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.contextmenu.SubMenu;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.emblow.envelofy.domain.Account;
import org.emblow.envelofy.service.AccountService;
import org.emblow.envelofy.ui.views.AccountView;

public class AccountCard extends Div {
    private final AccountService accountService;
    private final Runnable onModified;

    public AccountCard(Account account, AccountService accountService, Runnable onModified) {
        this.accountService = accountService;
        this.onModified = onModified;

        initializeCard(account);
    }

    private void initializeCard(Account account) {
        addClassName("account-card");
        getStyle()
            .set("background", "var(--card-background, linear-gradient(to bottom, #ffffff, #f9f9f9))")
            .set("border-radius", "12px")
            .set("box-shadow", "var(--card-shadow, 0 4px 8px rgba(0,0,0,0.1))")
            .set("padding", "var(--lumo-space-l)")
            .set("max-width", "320px")
            .set("width", "100%")
            .set("transition", "transform 0.2s, box-shadow 0.2s");

        // Hover effect
        getElement().addEventListener("mouseenter", e ->
            getStyle()
                .set("transform", "scale(1.03)")
                .set("box-shadow", "var(--card-shadow-hover, 0 6px 12px rgba(0,0,0,0.15))")
        );
        getElement().addEventListener("mouseleave", e ->
            getStyle()
                .set("transform", "scale(1)")
                .set("box-shadow", "var(--card-shadow, 0 4px 8px rgba(0,0,0,0.1))")
        );

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);

        // Header with name
        H4 name = new H4(account.getName());
        name.getStyle()
            .set("margin", "0")
            .set("font-size", "1.2em")
            .set("color", "var(--lumo-primary-text-color)");

        // Type as a badge
        Span type = new Span(account.getType().getDisplayName());
        type.getStyle()
            .set("background", "var(--type-bg, var(--lumo-contrast-10pct))")
            .set("color", "var(--type-text, var(--lumo-secondary-text-color))")
            .set("font-size", "var(--lumo-font-size-s)")
            .set("padding", "2px 8px")
            .set("border-radius", "12px");

        // Balance with highlight
        Span balance = new Span(String.format("$%.2f", account.getBalance()));
        balance.getStyle()
            .set("font-size", "1.5em")
            .set("font-weight", "bold")
            .set("color", "var(--balance-text, white)")
            .set("background", account.getBalance().signum() >= 0 ? "var(--balance-positive, #27ae60)" : "var(--balance-negative, #e74c3c)")
            .set("padding", "4px 12px")
            .set("border-radius", "8px");

        // Quick actions toolbar
        HorizontalLayout actions = new HorizontalLayout();
        actions.setWidthFull();
        actions.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        actions.setSpacing(false);
        actions.getStyle().set("gap", "var(--lumo-space-s)");

        Button viewButton = createActionButton(VaadinIcon.LIST, "View transactions",
            e -> onViewClicked(account));
        MenuBar menuBar = createMenuBar(account);

        actions.add(viewButton, menuBar);

        // Institution and account number
        if (account.getInstitution() != null && !account.getInstitution().isEmpty()) {
            Span institution = new Span(account.getInstitution());
            institution.getStyle()
                .set("color", "var(--institution-text, var(--lumo-secondary-text-color))")
                .set("font-size", "var(--lumo-font-size-s)");
            content.add(name, type, institution, balance, actions);
        } else {
            content.add(name, type, balance, actions);
        }

        if (account.getAccountNumber() != null && !account.getAccountNumber().isEmpty()) {
            Span accountNumber = new Span("****" + account.getAccountNumber());
            accountNumber.getStyle()
                .set("color", "var(--account-number-text, var(--lumo-tertiary-text-color))")
                .set("font-size", "var(--lumo-font-size-xs)");
            content.add(accountNumber);
        }

        add(content);

        // Apply theme styles for dark mode
        applyThemeStyles();
    }

    private Button createActionButton(VaadinIcon icon, String tooltip,
                                     ComponentEventListener<ClickEvent<Button>> listener) {
        Button button = new Button(new Icon(icon), listener);
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        button.getStyle()
            .set("border-radius", "50%")
            .set("padding", "var(--lumo-space-s)")
            .set("background", "var(--button-bg, var(--lumo-contrast-5pct))")
            .set("transition", "background 0.2s");
        button.getElement().setAttribute("title", tooltip);
        button.getElement().addEventListener("mouseover", e ->
            button.getStyle().set("background", "var(--button-bg-hover, var(--lumo-contrast-10pct))")
        );
        button.getElement().addEventListener("mouseout", e ->
            button.getStyle().set("background", "var(--button-bg, var(--lumo-contrast-5pct))")
        );

        return button;
    }

    private MenuBar createMenuBar(Account account) {
        MenuBar menuBar = new MenuBar();
        menuBar.getStyle()
            .set("min-width", "var(--lumo-button-size)")
            .set("min-height", "var(--lumo-button-size)");
        MenuItem menuItem = menuBar.addItem(new Icon(VaadinIcon.ELLIPSIS_DOTS_V));
        menuItem.getStyle().set("padding", "var(--lumo-space-s)");
        SubMenu subMenu = menuItem.getSubMenu();
        subMenu.addItem("Edit", e -> showEditDialog(account));
        subMenu.addItem("Delete", e -> showDeleteDialog(account));
        return menuBar;
    }

    private void applyThemeStyles() {
        // Inject custom CSS to handle light/dark mode using the root html's theme attribute.
        UI.getCurrent().getPage().executeJs(
            "var style = document.createElement('style');" +
            "style.textContent = `" +
            "html[theme='dark'] .account-card {" +
            "  --card-background: linear-gradient(to bottom, #2d3748, #1a202c);" +
            "  --card-shadow: 0 4px 8px rgba(0,0,0,0.3);" +
            "  --card-shadow-hover: 0 6px 12px rgba(0,0,0,0.4);" +
            "  --type-bg: #4a5568;" +
            "  --type-text: #e2e8f0;" +
            "  --balance-positive: #48bb78;" +
            "  --balance-negative: #f56565;" +
            "  --balance-text: #ffffff;" +
            "  --institution-text: #a0aec0;" +
            "  --account-number-text: #718096;" +
            "  --button-bg: #4a5568;" +
            "  --button-bg-hover: #718096;" +
            "}`;" +
            "document.head.appendChild(style);"
        );
    }


    private void onViewClicked(Account account) {
        if (account.getId() != null) {
            UI.getCurrent().navigate(AccountView.class, account.getId());
        } else {
            Notification.show("Account not saved yet. Please save the account before viewing details.",
                3000, Notification.Position.MIDDLE);
        }
    }

    private void showEditDialog(Account account) {
        new AccountDialog(accountService, onModified, account).open();
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
                Notification.show("Account deleted", 3000, Notification.Position.MIDDLE);
            } catch (Exception ex) {
                Notification notification = Notification.show(
                    "Error deleting account: " + ex.getMessage(),
                    3000, Notification.Position.MIDDLE
                );
                notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        dialog.open();
    }
}