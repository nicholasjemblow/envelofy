/*
    Copyright (C) 2025 Nicholas J Emblow
    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.
    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.
    You should have received a copy of the GNU General Public License
    along with this program.  If not, see http://www.gnu.org/licenses/.
*/
package org.emblow.envelofy.ui.components;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.contextmenu.SubMenu;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import org.emblow.envelofy.domain.Envelope;

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

        // Base styles with theme-aware variables
        getStyle()
            .set("background", "var(--card-background, linear-gradient(to bottom, #f1f8e9, #ffffff))")
            .set("border", "1px solid var(--lumo-contrast-10pct)")
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
        H4 name = new H4(envelope.getName());
        name.getStyle()
            .set("margin", "0")
            .set("font-size", "1.2em")
            .set("color", "var(--lumo-primary-text-color)");

        // Balance information with badges
        HorizontalLayout balanceLayout = new HorizontalLayout();
        balanceLayout.setWidthFull();
        balanceLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        Span available = new Span(currencyFormat.format(envelope.getAvailable()));
        available.getStyle()
            .set("background", "var(--available-bg, #2ecc71)")
            .set("color", "var(--available-text, white)")
            .set("padding", "4px 10px")
            .set("border-radius", "8px")
            .set("font-size", "var(--lumo-font-size-s)");

        Span allocated = new Span(currencyFormat.format(envelope.getAllocated()));
        allocated.getStyle()
            .set("background", "var(--allocated-bg, var(--lumo-contrast-20pct))")
            .set("color", "var(--allocated-text, var(--lumo-secondary-text-color))")
            .set("padding", "4px 10px")
            .set("border-radius", "8px")
            .set("font-size", "var(--lumo-font-size-s)");

        balanceLayout.add(available, allocated);

        // Progress bar wrapper
        Div progressWrapper = new Div();
        progressWrapper.getStyle().set("position", "relative");
        progressWrapper.setWidthFull();

        ProgressBar progress = new ProgressBar();
        progress.setMin(0);
        progress.setMax(100);
        double spent = envelope.getSpentPercentage();
        progress.setValue(spent);
        progress.setWidthFull();

        // Dynamic progress bar color
        String progressColor = spent < 70 ? "var(--progress-low, #2ecc71)"
                            : spent < 90 ? "var(--progress-mid, #f39c12)"
                            : "var(--progress-high, #e74c3c)";
        progress.getStyle().set("--lumo-primary-color", progressColor); // Use Lumo variable for progress bar color

        Span progressLabel = new Span(String.format("%.0f%%", spent));
        progressLabel.getStyle()
            .set("position", "absolute")
            .set("top", "0")
            .set("right", "var(--lumo-space-m)")
            .set("color", "var(--progress-label-text, white)")
            .set("font-size", "var(--lumo-font-size-s)");

        progressWrapper.add(progress, progressLabel);

        // Quick actions toolbar
        HorizontalLayout actions = new HorizontalLayout();
        actions.setWidthFull();
        actions.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        actions.setSpacing(false);
        actions.getStyle().set("gap", "var(--lumo-space-s)");

        Button addButton = createActionButton(VaadinIcon.PLUS, "Add funds", "var(--add-button-bg, #2ecc71)",
            e -> onAddClicked(envelope));
        Button moveButton = createActionButton(VaadinIcon.EXCHANGE, "Move funds", "var(--move-button-bg, #3498db)",
            e -> onMoveClicked(envelope));
        Button historyButton = createActionButton(VaadinIcon.LIST, "View history", "var(--history-button-bg, #9b59b6)",
            e -> onHistoryClicked(envelope));
        MenuBar menuBar = createMenuBar(envelope);

        actions.add(addButton, moveButton, historyButton, menuBar);

        content.add(name, balanceLayout, progressWrapper, actions);
        add(content);

        // Apply CSS to handle theme switching
        applyThemeStyles();
    }

    private Button createActionButton(VaadinIcon icon, String tooltip, String color,
                                     ComponentEventListener<ClickEvent<Button>> listener) {
        Button button = new Button(new Icon(icon), listener);
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        button.getStyle()
            .set("border-radius", "50%")
            .set("padding", "var(--lumo-space-s)")
            .set("background", color)
            .set("color", "var(--button-text, white)")
            .set("transition", "opacity 0.2s");
        button.getElement().addEventListener("mouseover", e ->
            button.getStyle().set("opacity", "0.9")
        );
        button.getElement().addEventListener("mouseout", e ->
            button.getStyle().set("opacity", "1")
        );

        return button;
    }

    private MenuBar createMenuBar(Envelope envelope) {
        MenuBar menuBar = new MenuBar();
        menuBar.getStyle()
            .set("min-width", "var(--lumo-button-size)")
            .set("min-height", "var(--lumo-button-size)");
        MenuItem menuItem = menuBar.addItem(new Icon(VaadinIcon.ELLIPSIS_DOTS_V));
        menuItem.getStyle().set("padding", "var(--lumo-space-s)");
        SubMenu subMenu = menuItem.getSubMenu();
        subMenu.addItem("Edit Name", e -> dialogs.showEditNameDialog(envelope, UI.getCurrent().getPage()::reload));
        subMenu.addItem("Budget Settings", e -> dialogs.showBudgetDialog(envelope, UI.getCurrent().getPage()::reload));
        subMenu.addItem("Delete", e -> dialogs.showDeleteEnvelopeDialog(envelope, UI.getCurrent().getPage()::reload));
        return menuBar;
    }

    private void applyThemeStyles() {
        // Inject custom CSS to handle light/dark mode using the root element's attribute.
        UI.getCurrent().getPage().executeJs(
            "var style = document.createElement('style');" +
            "style.textContent = `" +
            "html[theme='dark'] .envelope-card {" +
            "  --card-background: linear-gradient(to bottom, #2d3748, #1a202c);" +
            "  --card-shadow: 0 4px 8px rgba(0,0,0,0.3);" +
            "  --card-shadow-hover: 0 6px 12px rgba(0,0,0,0.4);" +
            "  --available-bg: #48bb78;" +
            "  --available-text: #ffffff;" +
            "  --allocated-bg: #4a5568;" +
            "  --allocated-text: #e2e8f0;" +
            "  --progress-low: #48bb78;" +
            "  --progress-mid: #ed8936;" +
            "  --progress-high: #f56565;" +
            "  --progress-label-text: #ffffff;" +
            "  --add-button-bg: #48bb78;" +
            "  --move-button-bg: #4299e1;" +
            "  --history-button-bg: #9f7aea;" +
            "  --button-text: #ffffff;" +
            "}" +
            "`;" +
            "document.head.appendChild(style);"
        );
    }


    private void onAddClicked(Envelope envelope) {
        dialogs.showAddFundsDialog(envelope, () -> UI.getCurrent().getPage().reload());
    }

    private void onMoveClicked(Envelope envelope) {
        dialogs.showMoveFundsDialog(envelope, () -> UI.getCurrent().getPage().reload());
    }

    private void onHistoryClicked(Envelope envelope) {
        UI.getCurrent().navigate("envelopes/" + envelope.getId());
    }
}