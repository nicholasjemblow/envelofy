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
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.contextmenu.ContextMenu;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.contextmenu.SubMenu;
import com.vaadin.flow.component.menubar.MenuBar;
import org.emblow.envelopify.domain.Envelope;

import java.text.NumberFormat;
import java.util.Locale;

public class EnvelopeCard extends Div {
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.US);
    private final EnvelopeManagementDialogs dialogs;

    public EnvelopeCard(Envelope envelope, EnvelopeManagementDialogs dialogs) {
        this.dialogs = dialogs;
        initializeCard(envelope);
    }

    private void initializeCard(Envelope envelope) {
        addClassName("envelope-card");
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

        // Header with name
        H4 name = new H4(envelope.getName());
        name.getStyle().setMargin("0");

        // Balance information
        HorizontalLayout balanceLayout = new HorizontalLayout();
        balanceLayout.setWidthFull();
        balanceLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        Span available = new Span("Available: " + 
            currencyFormat.format(envelope.getAvailable()));
        Span allocated = new Span("Allocated: " + 
            currencyFormat.format(envelope.getAllocated()));

        balanceLayout.add(available, allocated);

        // Progress bar
        ProgressBar progress = new ProgressBar();
        progress.setMin(0);
        progress.setMax(100);
        progress.setValue(envelope.getSpentPercentage());
        progress.setWidthFull();

        // Quick actions
        HorizontalLayout actions = new HorizontalLayout();
        actions.setWidthFull();
        actions.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        Button addButton = new Button(
            new Icon(VaadinIcon.PLUS),
            e -> onAddClicked(envelope)
        );
        addButton.getElement().setAttribute("title", "Add funds");

        Button moveButton = new Button(
            new Icon(VaadinIcon.EXCHANGE),
            e -> onMoveClicked(envelope)
        );
        moveButton.getElement().setAttribute("title", "Move funds");

        Button historyButton = new Button(
            new Icon(VaadinIcon.LIST),
            e -> onHistoryClicked(envelope)
        );
        historyButton.getElement().setAttribute("title", "View history");

        MenuBar menuBar = new MenuBar();
        menuBar.getStyle()
            .set("min-width", "var(--lumo-button-size)")
            .set("min-height", "var(--lumo-button-size)");
        MenuItem menuItem = menuBar.addItem(new Icon(VaadinIcon.ELLIPSIS_DOTS_V));
        SubMenu subMenu = menuItem.getSubMenu();
        subMenu.addItem("Edit Name", e -> dialogs.showEditNameDialog(envelope, () -> {
            UI.getCurrent().getPage().reload();
        }));
        subMenu.addItem("Budget Settings", e -> dialogs.showBudgetDialog(envelope, () -> {
            UI.getCurrent().getPage().reload();
        }));
        subMenu.addItem("Delete", e -> dialogs.showDeleteEnvelopeDialog(envelope, () -> {
            UI.getCurrent().getPage().reload();
        }));

        actions.add(addButton, moveButton, historyButton, menuBar);

        // Add all components
        content.add(name, balanceLayout, progress, actions);
        add(content);  // This should be the last line of initializeCard
    }

    private void onAddClicked(Envelope envelope) {
        dialogs.showAddFundsDialog(envelope, () -> {
            // Refresh the UI after success
            UI.getCurrent().getPage().reload();
        });
    }

    private void onMoveClicked(Envelope envelope) {
        dialogs.showMoveFundsDialog(envelope, () -> {
            // Refresh the UI after success
            UI.getCurrent().getPage().reload();
        });
    }

    private void onHistoryClicked(Envelope envelope) {
        UI.getCurrent().navigate("envelopes/" + envelope.getId());
    }

}