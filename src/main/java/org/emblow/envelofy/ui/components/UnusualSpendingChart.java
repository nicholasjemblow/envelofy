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
import com.vaadin.flow.component.littemplate.LitTemplate;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.emblow.envelofy.domain.Envelope;
import org.emblow.envelofy.service.ml.SpendingInsight;
import org.emblow.envelofy.service.ml.SpendingInsightService;
import org.emblow.envelofy.service.ml.SpendingInsightType;

import java.util.List;
import java.util.stream.Collectors;

@Tag("unusual-spending-chart")
@JsModule("./unusual-spending-chart.js")
@AnonymousAllowed
public class UnusualSpendingChart extends LitTemplate {
    
    private final SpendingInsightService insightService;
    private final String envelopeName;

    public UnusualSpendingChart(SpendingInsightService insightService, Envelope envelope) {
        this.insightService = insightService;
        this.envelopeName = envelope.getName();
        
        // Set sizing via style
        getElement().getStyle().set("display", "block");
        getElement().getStyle().set("height", "400px");
        getElement().getStyle().set("width", "100%");
        
        refreshData();
    }

    private void refreshData() {
        // Get unusual spending insights for this envelope
        List<SpendingInsight> insights = insightService.generateInsights().stream()
            .filter(i -> i.getType() == SpendingInsightType.UNUSUAL_SPENDING 
                && i.getMessage().contains(envelopeName))
            .collect(Collectors.toList());

        // Build labels and data arrays using valid JSON (double quotes for strings)
        StringBuilder labels = new StringBuilder("[");
        StringBuilder data = new StringBuilder("[");

        for (int i = 0; i < insights.size(); i++) {
            if (i > 0) {
                labels.append(",");
                data.append(",");
            }

            // Use double quotes so that JSON.parse will succeed in JS.
            labels.append("\"Event ").append(i + 1).append("\"");
            data.append(String.format("%.2f", insights.get(i).getConfidence() * 100));
        }

        labels.append("]");
        data.append("]");

        // Update the chart properties
        getElement().setProperty("labels", labels.toString());
        getElement().setProperty("data", data.toString());
    }

}
