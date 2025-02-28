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
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.html.H3;
import org.emblow.envelofy.domain.Envelope;
import org.emblow.envelofy.service.ml.SpendingInsight;
import org.emblow.envelofy.service.ml.SpendingInsightService;

import java.util.List;
import java.util.stream.Collectors;

public class MLInsightsTable extends VerticalLayout {
    
    private final SpendingInsightService insightService;
    private final String envelopeName;
    private final Grid<SpendingInsight> grid;

    public MLInsightsTable(SpendingInsightService insightService, Envelope envelope) {
        this.insightService = insightService;
        this.envelopeName = envelope.getName();
        
        setPadding(true);
        setSpacing(true);
        
        add(new H3("ML-Generated Insights"));
        
        // Initialize grid
        grid = new Grid<>();
        grid.addColumn(insight -> insight.getType().toString())
            .setHeader("Type")
            .setAutoWidth(true);
            
        grid.addColumn(SpendingInsight::getMessage)
            .setHeader("Description")
            .setAutoWidth(true)
            .setFlexGrow(1);
            
        grid.addColumn(insight -> String.format("%.0f%%", insight.getConfidence() * 100))
            .setHeader("Confidence")
            .setAutoWidth(true);
            
        grid.setWidthFull();
        
        add(grid);
        refreshData();
    }

    public void refreshData() {
        // Get insights relevant to this envelope
        List<SpendingInsight> insights = insightService.generateInsights().stream()
            .filter(insight -> insight.getMessage().contains(envelopeName))
            .collect(Collectors.toList());
            
        grid.setItems(insights);
    }
}