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
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import org.emblow.envelopify.domain.Envelope;
import org.emblow.envelopify.domain.Transaction;
import org.emblow.envelopify.service.PatternService;

import java.util.Map;

public class EnvelopeSuggestions extends HorizontalLayout {
    private final ComboBox<Envelope> envelopeField;
    private final PatternService patternService;

    public EnvelopeSuggestions(
        ComboBox<Envelope> envelopeField,
        PatternService patternService
    ) {
        this.envelopeField = envelopeField;
        this.patternService = patternService;
        
        setSpacing(true);
        setAlignItems(Alignment.CENTER);
        setVisible(false);
    }

    public void updateSuggestions(Transaction transaction) {
        removeAll();
        
        if (transaction.getDescription() == null || transaction.getDescription().trim().isEmpty()) {
            setVisible(false);
            return;
        }

        Map<Envelope, Double> suggestions = patternService.suggestEnvelopes(transaction);
        if (!suggestions.isEmpty()) {
            setVisible(true);
            
            add(new Span("Suggested: "));
            
            suggestions.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(3)  // Show top 3 suggestions
                .forEach(entry -> {
                    Button suggestionButton = new Button(
                        entry.getKey().getName(),
                        click -> envelopeField.setValue(entry.getKey())
                    );
                    suggestionButton.addThemeVariants(ButtonVariant.LUMO_SMALL);
                    add(suggestionButton);
                });
        } else {
            setVisible(false);
        }
    }
}