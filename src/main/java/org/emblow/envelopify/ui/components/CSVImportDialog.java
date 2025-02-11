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
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.select.Select;
import org.emblow.envelopify.domain.Envelope;
import org.emblow.envelopify.service.CSVImportService;
import org.emblow.envelopify.service.CSVImportService.CSVMapping;
import org.emblow.envelopify.service.EnvelopeService;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import org.emblow.envelopify.domain.Account;
import org.emblow.envelopify.service.AccountService;



public class CSVImportDialog extends Dialog {
    private final CSVImportService csvImportService;
    private final EnvelopeService envelopeService;
    private final AccountService accountService;
    private final Runnable onSuccess;

    private final MemoryBuffer buffer;
    private final Upload upload;
    private final ComboBox<Envelope> defaultEnvelopeField;
    private final ComboBox<Account> accountField;
    private final Select<String> presetMappingField;
    private final TextField delimiterField;
    private final NumberField dateColumnField;
    private final TextField dateFormatField;
    private final NumberField descriptionColumnField;
    private final NumberField amountColumnField;
    private final NumberField amountMultiplierField;

    public CSVImportDialog(
        CSVImportService csvImportService,
        EnvelopeService envelopeService,
        AccountService accountService,
        Runnable onSuccess
    ) {
        this.csvImportService = csvImportService;
        this.envelopeService = envelopeService;
        this.accountService = accountService;
        this.onSuccess = onSuccess;

        setModal(true);
        setDraggable(true);
        setResizable(true);

        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);
        layout.setSpacing(true);

        // Title
        H3 title = new H3("Import Transactions from CSV");
        layout.add(title);

        // File upload
        buffer = new MemoryBuffer();
        upload = new Upload(buffer);
        upload.setAcceptedFileTypes(".csv");
        upload.addSucceededListener(event -> {
            Notification.show(
                "File uploaded successfully: " + event.getFileName(),
                3000,
                Notification.Position.MIDDLE
            );
        });
        layout.add(upload);

        // Account selection
        accountField = new ComboBox<>("Account");
        accountField.setItemLabelGenerator(account -> 
            String.format("%s (%s)", 
                account.getName(),
                account.getType().getDisplayName()
            )
        );
        accountField.setItems(accountService.getAllAccounts());
        accountField.setRequired(true);
        accountField.setWidthFull();
        layout.add(accountField);

        // Preset mappings dropdown
        presetMappingField = new Select<>();
        presetMappingField.setLabel("Preset Format");
        presetMappingField.setItems("Custom", "Chase", "Bank of America");
        presetMappingField.setValue("Custom");
        presetMappingField.addValueChangeListener(e -> updateMappingFields(e.getValue()));
        layout.add(presetMappingField);

        // CSV mapping fields
        delimiterField = new TextField("Delimiter");
        delimiterField.setValue(",");

        dateColumnField = new NumberField("Date Column Index");
        dateColumnField.setValue(0.0);
        dateColumnField.setStep(1.0);
        dateColumnField.setMin(0);

        dateFormatField = new TextField("Date Format");
        dateFormatField.setValue("MM/dd/yyyy");

        descriptionColumnField = new NumberField("Description Column Index");
        descriptionColumnField.setValue(1.0);
        descriptionColumnField.setStep(1.0);
        descriptionColumnField.setMin(0);

        amountColumnField = new NumberField("Amount Column Index");
        amountColumnField.setValue(2.0);
        amountColumnField.setStep(1.0);
        amountColumnField.setMin(0);

        amountMultiplierField = new NumberField("Amount Multiplier");
        amountMultiplierField.setValue(1.0);
        amountMultiplierField.setHelperText("Use -1 if amounts need to be reversed");

        layout.add(
            delimiterField,
            dateColumnField,
            dateFormatField,
            descriptionColumnField,
            amountColumnField,
            amountMultiplierField
        );

        // Default envelope selector
        defaultEnvelopeField = new ComboBox<>("Default Envelope");
        defaultEnvelopeField.setItemLabelGenerator(Envelope::getName);
        defaultEnvelopeField.setItems(envelopeService.getAllEnvelopes());
        defaultEnvelopeField.setRequired(true);
        layout.add(defaultEnvelopeField);

        // Help text
        Paragraph help = new Paragraph(
            "Column indices start at 0. Date format should match the pattern in your CSV file."
        );
        layout.add(help);

        // Buttons
        HorizontalLayout buttons = new HorizontalLayout();
        buttons.setJustifyContentMode(JustifyContentMode.END);
        buttons.setWidthFull();

        Button cancelButton = new Button("Cancel", e -> close());

        Button importButton = new Button("Import", e -> importData());
        importButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        buttons.add(cancelButton, importButton);
        layout.add(buttons);

        add(layout);
    }

    private void updateMappingFields(String preset) {
        switch (preset) {
            case "Chase" -> {
                delimiterField.setValue(",");
                dateColumnField.setValue(0.0);
                dateFormatField.setValue("MM/dd/yyyy");
                descriptionColumnField.setValue(2.0);
                amountColumnField.setValue(3.0);
                amountMultiplierField.setValue(1.0);
            }
            case "Bank of America" -> {
                delimiterField.setValue(",");
                dateColumnField.setValue(0.0);
                dateFormatField.setValue("MM/dd/yyyy");
                descriptionColumnField.setValue(1.0);
                amountColumnField.setValue(2.0);
                amountMultiplierField.setValue(1.0);
            }
            // Add more presets as needed
        }
    }

    private void importData() {
        try {
            if (defaultEnvelopeField.isEmpty()) {
                throw new IllegalArgumentException("Please select a default envelope");
            }
            if (accountField.isEmpty()) {
                throw new IllegalArgumentException("Please select an account");
            }

            // Read file content
            InputStream is = buffer.getInputStream();
            String content = new Scanner(is, StandardCharsets.UTF_8)
                .useDelimiter("\\A")
                .next();

            // Create mapping from form fields
            CSVMapping mapping = new CSVMapping(
                true, // Assume headers for now
                delimiterField.getValue(),
                dateColumnField.getValue().intValue(),
                dateFormatField.getValue(),
                descriptionColumnField.getValue().intValue(),
                amountColumnField.getValue().intValue(),
                amountMultiplierField.getValue()
            );

            // Import data
            CSVImportService.ImportResult result = csvImportService.importTransactions(
                content,
                mapping,
                defaultEnvelopeField.getValue().getId(),
                accountField.getValue().getId()
            );

            // Show results
            String message = String.format(
                "Imported %d transactions successfully. %d failed.",
                result.getSuccessful(),
                result.getFailed()
            );

            if (result.getFailed() > 0) {
                message += "\nErrors:\n" + String.join("\n", result.getErrors());
            }

            Notification notification = Notification.show(
                message,
                5000,
                Notification.Position.MIDDLE
            );

            if (result.getFailed() > 0) {
                notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
            } else {
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            }

            // Close dialog and refresh parent view
            close();
            onSuccess.run();

        } catch (IllegalArgumentException ex) {
            Notification notification = Notification.show(
                "Error importing data: " + ex.getMessage(),
                5000,
                Notification.Position.MIDDLE
            );
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }
}