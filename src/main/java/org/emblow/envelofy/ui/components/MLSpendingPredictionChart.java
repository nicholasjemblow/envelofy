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
import org.emblow.envelofy.service.ml.AdvancedMLService;

import java.time.YearMonth;
import java.util.List;
import java.util.ArrayList;

@Tag("ml-spending-prediction-chart")
@JsModule("./ml-spending-prediction-chart.js")
@AnonymousAllowed
public class MLSpendingPredictionChart extends LitTemplate {
    
    private final AdvancedMLService advancedMLService;
    private final Envelope envelope;

    public MLSpendingPredictionChart(AdvancedMLService advancedMLService, Envelope envelope) {
        this.advancedMLService = advancedMLService;
        this.envelope = envelope;
        
        // Set sizing via style
        getElement().getStyle().set("display", "block");
        getElement().getStyle().set("height", "400px");
        getElement().getStyle().set("width", "100%");
        
        refreshData();
    }

    private void refreshData() {
        // Get forecast data for the next 6 months
        List<AdvancedMLService.AccountAnalysis> analyses = advancedMLService.analyzeAccounts();

        // Find the relevant analysis for this envelope
        double trend = analyses.stream()
            .flatMap(a -> a.getEnvelopeMetrics().entrySet().stream())
            .filter(e -> e.getKey().getId().equals(envelope.getId()))
            .findFirst()
            .map(e -> e.getValue().spendingTrend())
            .orElse(0.0);

        // Build labels and data arrays using valid JSON (double quotes for strings)
        StringBuilder labels = new StringBuilder("[");
        StringBuilder data = new StringBuilder("[");

        YearMonth current = YearMonth.now().plusMonths(1);
        double baseAmount = envelope.getCurrentMonthSpent().doubleValue();
        if (baseAmount == 0) {
            baseAmount = envelope.getMonthlyBudget().doubleValue();
        }

        for (int i = 0; i < 6; i++) {
            if (i > 0) {
                labels.append(",");
                data.append(",");
            }

            // Use double quotes for JSON string values
            labels.append("\"")
                  .append(current.getMonth().name().substring(0, 3))
                  .append(" ").append(current.getYear())
                  .append("\"");

            // Calculate predicted amount using the trend
            double predicted = baseAmount * (1 + (trend * (i + 1)));
            data.append(String.format("%.2f", predicted));

            current = current.plusMonths(1);
        }

        labels.append("]");
        data.append("]");

        // Update the chart
        getElement().setProperty("labels", labels.toString());
        getElement().setProperty("data", data.toString());
    }

}