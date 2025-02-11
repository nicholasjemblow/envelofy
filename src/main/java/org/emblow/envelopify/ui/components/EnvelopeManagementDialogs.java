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
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import org.emblow.envelopify.domain.Envelope;
import org.emblow.envelopify.service.EnvelopeService;
import java.math.BigDecimal;
import java.util.List;

public class EnvelopeManagementDialogs {
    private final EnvelopeService envelopeService;
    
    public EnvelopeManagementDialogs(EnvelopeService envelopeService) {
        this.envelopeService = envelopeService;
    }

    public void showAddFundsDialog(Envelope envelope, Runnable onSuccess) {
        Dialog dialog = new Dialog();
        dialog.setModal(true);
        dialog.setDraggable(true);
        
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);
        layout.setSpacing(true);
        layout.setAlignItems(Alignment.STRETCH);
        
        H3 title = new H3("Add Funds to " + envelope.getName());
        
        BigDecimalField amountField = new BigDecimalField("Amount");
        amountField.setPrefixComponent(new Span("$"));
        amountField.setValue(BigDecimal.ZERO);
        amountField.setWidthFull();
        
        HorizontalLayout buttons = new HorizontalLayout();
        buttons.setJustifyContentMode(JustifyContentMode.END);
        buttons.setWidthFull();
        
        Button cancelButton = new Button("Cancel", e -> dialog.close());
        
        Button addButton = new Button("Add Funds", e -> {
            try {
                BigDecimal amount = amountField.getValue();
                if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("Please enter a valid amount");
                }
                envelope.allocate(amount);
                envelopeService.save(envelope);
                dialog.close();
                onSuccess.run();
                Notification.show(
                    "Added " + amount + " to " + envelope.getName(),
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
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        
        buttons.add(cancelButton, addButton);
        layout.add(title, amountField, buttons);
        dialog.add(layout);
        dialog.open();
    }

    public void showMoveFundsDialog(Envelope sourceEnvelope, Runnable onSuccess) {
        Dialog dialog = new Dialog();
        dialog.setModal(true);
        dialog.setDraggable(true);
        
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);
        layout.setSpacing(true);
        layout.setAlignItems(Alignment.STRETCH);
        
        H3 title = new H3("Move Funds from " + sourceEnvelope.getName());
        
        // Target envelope selector
        ComboBox<Envelope> targetEnvelopeField = new ComboBox<>("To Envelope");
        targetEnvelopeField.setItemLabelGenerator(Envelope::getName);
        List<Envelope> otherEnvelopes = envelopeService.getAllEnvelopes().stream()
            .filter(e -> !e.getId().equals(sourceEnvelope.getId()))
            .toList();
        targetEnvelopeField.setItems(otherEnvelopes);
        targetEnvelopeField.setWidthFull();
        
        // Amount field
        BigDecimalField amountField = new BigDecimalField("Amount");
        amountField.setPrefixComponent(new Span("$"));
        amountField.setValue(BigDecimal.ZERO);
        amountField.setWidthFull();
        
        // Available amount display
        Span availableAmount = new Span(
            "Available: $" + sourceEnvelope.getAvailable()
        );
        
        HorizontalLayout buttons = new HorizontalLayout();
        buttons.setJustifyContentMode(JustifyContentMode.END);
        buttons.setWidthFull();
        
        Button cancelButton = new Button("Cancel", e -> dialog.close());
        
        Button moveButton = new Button("Move Funds", e -> {
            try {
                Envelope targetEnvelope = targetEnvelopeField.getValue();
                BigDecimal amount = amountField.getValue();
                
                if (targetEnvelope == null) {
                    throw new IllegalArgumentException("Please select a target envelope");
                }
                if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("Please enter a valid amount");
                }
                
                envelopeService.reallocate(
                    sourceEnvelope.getId(),
                    targetEnvelope.getId(),
                    amount
                );
                dialog.close();
                onSuccess.run();
                Notification.show(
                    "Moved " + amount + " from " + sourceEnvelope.getName() +
                    " to " + targetEnvelope.getName(),
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
        moveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        
        buttons.add(cancelButton, moveButton);
        layout.add(title, targetEnvelopeField, amountField, availableAmount, buttons);
        dialog.add(layout);
        dialog.open();
    }

    public void showNewEnvelopeDialog(Runnable onSuccess) {
        Dialog dialog = new Dialog();
        dialog.setModal(true);
        dialog.setDraggable(true);
        
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);
        layout.setSpacing(true);
        layout.setAlignItems(Alignment.STRETCH);
        
        H3 title = new H3("Create New Envelope");
        
        TextField nameField = new TextField("Name");
        nameField.setWidthFull();
        nameField.setRequired(true);
        
        BigDecimalField amountField = new BigDecimalField("Initial Amount");
        amountField.setPrefixComponent(new Span("$"));
        amountField.setValue(BigDecimal.ZERO);
        amountField.setWidthFull();
        
        HorizontalLayout buttons = new HorizontalLayout();
        buttons.setJustifyContentMode(JustifyContentMode.END);
        buttons.setWidthFull();
        
        Button cancelButton = new Button("Cancel", e -> dialog.close());
        
        Button createButton = new Button("Create", e -> {
            try {
                String name = nameField.getValue();
                BigDecimal amount = amountField.getValue();
                
                if (name == null || name.trim().isEmpty()) {
                    throw new IllegalArgumentException("Please enter a name for the envelope");
                }
                if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
                    throw new IllegalArgumentException("Amount cannot be negative");
                }
                
                Envelope envelope = envelopeService.createEnvelope(name.trim(), amount);
                dialog.close();
                onSuccess.run();
                Notification.show(
                    "Created new envelope: " + envelope.getName(),
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
        createButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        
        buttons.add(cancelButton, createButton);
        layout.add(title, nameField, amountField, buttons);
        dialog.add(layout);
        dialog.open();
    }

    public void showEditNameDialog(Envelope envelope, Runnable onSuccess) {
        Dialog dialog = new Dialog();
        dialog.setModal(true);
        dialog.setDraggable(true);
        
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);
        layout.setSpacing(true);
        layout.setAlignItems(Alignment.STRETCH);
        
        H3 title = new H3("Edit Envelope Name");
        
        TextField nameField = new TextField("New Name", envelope.getName());
        nameField.setWidthFull();
        nameField.setRequired(true);
        
        HorizontalLayout buttons = new HorizontalLayout();
        buttons.setJustifyContentMode(JustifyContentMode.END);
        buttons.setWidthFull();
        
        Button cancelButton = new Button("Cancel", e -> dialog.close());
        
        Button saveButton = new Button("Save", e -> {
            try {
                String newName = nameField.getValue();
                if (newName == null || newName.trim().isEmpty()) {
                    throw new IllegalArgumentException("Please enter a valid name");
                }
                envelope.setName(newName.trim());
                envelopeService.save(envelope);
                dialog.close();
                onSuccess.run();
                Notification.show(
                    "Envelope name updated to " + newName,
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
        layout.add(title, nameField, buttons);
        dialog.add(layout);
        dialog.open();
    }

    public void showDeleteEnvelopeDialog(Envelope envelope, Runnable onSuccess) {
        Dialog dialog = new Dialog();
        dialog.setModal(true);
        dialog.setDraggable(true);
        
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);
        layout.setSpacing(true);
        layout.setAlignItems(Alignment.STRETCH);
        
        H3 title = new H3("Delete Envelope: " + envelope.getName());
        
        // Warning text
        Span warningText = new Span("Please select where to move the existing transactions:");
        
        // Target envelope selector (excluding current envelope)
        ComboBox<Envelope> targetEnvelopeField = new ComboBox<>("Move transactions to");
        targetEnvelopeField.setItemLabelGenerator(Envelope::getName);
        List<Envelope> otherEnvelopes = envelopeService.getAllEnvelopes().stream()
            .filter(e -> !e.getId().equals(envelope.getId()))
            .toList();
        targetEnvelopeField.setItems(otherEnvelopes);
        targetEnvelopeField.setWidthFull();
        targetEnvelopeField.setRequired(true);
        
        // Display current balance
        Span balanceInfo = new Span(
            "Current balance: $" + envelope.getAvailable()
        );
        
        HorizontalLayout buttons = new HorizontalLayout();
        buttons.setJustifyContentMode(JustifyContentMode.END);
        buttons.setWidthFull();
        
        Button cancelButton = new Button("Cancel", e -> dialog.close());
        
        Button deleteButton = new Button("Delete", e -> {
            try {
                Envelope targetEnvelope = targetEnvelopeField.getValue();
                if (targetEnvelope == null) {
                    throw new IllegalArgumentException("Please select a target envelope for the transactions");
                }
                
                envelopeService.deleteEnvelope(envelope.getId(), targetEnvelope.getId());
                dialog.close();
                onSuccess.run();
                
                Notification.show(
                    "Envelope deleted and transactions moved to " + targetEnvelope.getName(),
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
        deleteButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        
        buttons.add(cancelButton, deleteButton);
        layout.add(title, warningText, targetEnvelopeField, balanceInfo, buttons);
        dialog.add(layout);
        dialog.open();
    }
    
    public void showBudgetDialog(Envelope envelope, Runnable onSuccess) {
        Dialog dialog = new Dialog();
        dialog.setModal(true);
        dialog.setDraggable(true);

        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);
        layout.setSpacing(true);
        layout.setAlignItems(Alignment.STRETCH);

        H3 title = new H3("Budget Settings: " + envelope.getName());

        // Monthly budget field
        BigDecimalField monthlyBudgetField = new BigDecimalField("Monthly Budget Target");
        monthlyBudgetField.setPrefixComponent(new Span("$"));
        monthlyBudgetField.setValue(envelope.getMonthlyBudget());
        monthlyBudgetField.setWidthFull();

        // Current allocation field
        BigDecimalField allocationField = new BigDecimalField("Current Allocation");
        allocationField.setPrefixComponent(new Span("$"));
        allocationField.setValue(envelope.getAllocated());
        allocationField.setWidthFull();

        // Helper text
        Span helpText = new Span("The monthly budget is your target spending. The allocation is your currently available funds.");
        helpText.getStyle().set("color", "var(--lumo-secondary-text-color)");

        // Buttons
        HorizontalLayout buttons = new HorizontalLayout();
        buttons.setJustifyContentMode(JustifyContentMode.END);
        buttons.setWidthFull();

        Button cancelButton = new Button("Cancel", e -> dialog.close());

        Button saveButton = new Button("Save", e -> {
            try {
                BigDecimal newBudget = monthlyBudgetField.getValue();
                BigDecimal newAllocation = allocationField.getValue();

                if (newBudget == null || newBudget.compareTo(BigDecimal.ZERO) < 0) {
                    throw new IllegalArgumentException("Please enter a valid budget amount");
                }
                if (newAllocation == null || newAllocation.compareTo(BigDecimal.ZERO) < 0) {
                    throw new IllegalArgumentException("Please enter a valid allocation amount");
                }

                // Update budget
                envelope.setMonthlyBudget(newBudget);

                // Handle allocation change
                BigDecimal currentAllocation = envelope.getAllocated();
                if (newAllocation.compareTo(currentAllocation) > 0) {
                    envelope.allocate(newAllocation.subtract(currentAllocation));
                } else if (newAllocation.compareTo(currentAllocation) < 0) {
                    envelope.withdraw(currentAllocation.subtract(newAllocation));
                }

                envelopeService.save(envelope);
                dialog.close();
                onSuccess.run();

                Notification.show(
                    "Budget settings updated",
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
        layout.add(title, monthlyBudgetField, allocationField, helpText, buttons);
        dialog.add(layout);
        dialog.open();
    }
}