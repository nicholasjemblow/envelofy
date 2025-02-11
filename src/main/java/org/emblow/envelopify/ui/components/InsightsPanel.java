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
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.emblow.envelopify.service.ml.SpendingInsight;
import org.emblow.envelopify.service.ml.SpendingInsightType;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class InsightsPanel extends VerticalLayout {
    
    public InsightsPanel() {
        addClassName("insights-panel");
        setSpacing(true);
        setPadding(true);
        
        getStyle()
            .set("background-color", "var(--lumo-base-color)")
            .set("border-radius", "var(--lumo-border-radius-l)")
            .set("box-shadow", "var(--lumo-box-shadow-s)");
    }
    
    public void setInsights(List<SpendingInsight> insights) {
        removeAll();
        
        add(new H3("Smart Insights"));
        
        if (insights.isEmpty()) {
            add(new Span("No insights available yet. Add more transactions to get personalized insights."));
            return;
        }

        // Group insights by type
        Map<SpendingInsightType, List<SpendingInsight>> groupedInsights = insights.stream()
            .collect(Collectors.groupingBy(SpendingInsight::getType));

        // Add insights by type in a specific order
        addInsightGroup(groupedInsights, SpendingInsightType.UNUSUAL_SPENDING, 
            VaadinIcon.EXCLAMATION_CIRCLE, "var(--lumo-error-color)");
        addInsightGroup(groupedInsights, SpendingInsightType.RECURRING_PAYMENT, 
            VaadinIcon.CLOCK, "var(--lumo-primary-color)");
        addInsightGroup(groupedInsights, SpendingInsightType.PREDICTED_EXPENSE, 
            VaadinIcon.CHART_TIMELINE, "var(--lumo-success-color)");
        addInsightGroup(groupedInsights, SpendingInsightType.BUDGET_SUGGESTION, 
            VaadinIcon.PIGGY_BANK, "var(--lumo-primary-color)");
        addInsightGroup(groupedInsights, SpendingInsightType.SEASONAL_PATTERN, 
            VaadinIcon.CALENDAR, "var(--lumo-contrast-color)");
        addInsightGroup(groupedInsights, SpendingInsightType.REALLOCATION_SUGGESTION, 
            VaadinIcon.EXCHANGE, "var(--lumo-primary-color)");
    }
    
    private void addInsightGroup(
        Map<SpendingInsightType, List<SpendingInsight>> groupedInsights,
        SpendingInsightType type,
        VaadinIcon icon,
        String color
    ) {
        List<SpendingInsight> insights = groupedInsights.get(type);
        if (insights != null && !insights.isEmpty()) {
            insights.forEach(insight -> {
                Div card = createInsightCard(insight, icon, color);
                add(card);
            });
        }
    }
    
    private Div createInsightCard(SpendingInsight insight, VaadinIcon iconType, String color) {
        Div card = new Div();
        card.addClassName("insight-card");
        card.getStyle()
            .set("padding", "var(--lumo-space-m)")
            .set("border-radius", "var(--lumo-border-radius-m)")
            .set("background-color", "var(--lumo-base-color)")
            .set("border-left", "4px solid " + color)
            .set("margin-bottom", "var(--lumo-space-m)")
            .set("box-shadow", "var(--lumo-box-shadow-xs)");
        
        Icon icon = iconType.create();
        icon.setColor(color);
        icon.getStyle().set("margin-right", "var(--lumo-space-s)");
        
        Span message = new Span(insight.getMessage());
        message.getStyle()
            .set("color", "var(--lumo-body-text-color)")
            .set("font-size", "var(--lumo-font-size-s)");
        
        Div confidenceIndicator = new Div();
        confidenceIndicator.getStyle()
            .set("height", "4px")
            .set("background-color", color)
            .set("margin-top", "var(--lumo-space-s)")
            .set("border-radius", "var(--lumo-border-radius-s)")
            .set("opacity", String.valueOf(insight.getConfidence()));
        
        card.add(icon, message, confidenceIndicator);
        return card;
    }
}
