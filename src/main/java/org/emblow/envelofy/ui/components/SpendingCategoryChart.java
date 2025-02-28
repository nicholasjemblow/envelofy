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

import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.littemplate.LitTemplate;
import elemental.json.Json;
import elemental.json.JsonArray;
import elemental.json.JsonObject;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.emblow.envelofy.domain.Account;
import org.emblow.envelofy.domain.Transaction;
import org.emblow.envelofy.domain.TransactionType;

@Tag("spending-category-chart")
@JsModule("./spending-category-chart.js")
public class SpendingCategoryChart extends LitTemplate {
    private final Account account;

    public SpendingCategoryChart(Account account) {
        this.account = account;
        refreshData();
    }

    public void refreshData() {
        // Group transactions by envelope and calculate totals
        Map<String, BigDecimal> spendingByCategory = account.getTransactions().stream()
            .filter(tx -> tx.getType() == TransactionType.EXPENSE)
            .filter(tx -> tx.getDate().isAfter(LocalDateTime.now().minusMonths(1)))
            .collect(Collectors.groupingBy(
                tx -> tx.getEnvelope().getName(),
                Collectors.reducing(
                    BigDecimal.ZERO,
                    Transaction::getAmount,
                    BigDecimal::add
                )
            ));

        // Convert to lists for chart data
        List<String> labels = new ArrayList<>(spendingByCategory.keySet());
        List<BigDecimal> values = labels.stream()
            .map(spendingByCategory::get)
            .collect(Collectors.toList());

        // Generate colors for pie slices
        List<String> colors = generateColors(labels.size());

        // Create dataset object
        JsonObject dataset = Json.createObject();
        dataset.put("data", convertToJsonArray(values));
        dataset.put("backgroundColor", convertToJsonArray(colors));

        JsonArray datasets = Json.createArray();
        datasets.set(0, dataset);

        // Create data object
        JsonObject data = Json.createObject();
        data.put("labels", convertToJsonArray(labels));
        data.put("datasets", datasets);

        // Set chartData to the data object directly
        getElement().setProperty("chartData", data.toJson());
    }

    private List<String> generateColors(int count) {
        List<String> colors = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double hue = (360.0 / count) * i;
            colors.add(String.format("hsla(%f, 70%%, 60%%, 0.8)", hue));
        }
        return colors;
    }

    private JsonArray convertToJsonArray(List<?> list) {
        JsonArray array = Json.createArray();
        for (int i = 0; i < list.size(); i++) {
            Object value = list.get(i);
            if (value instanceof BigDecimal) {
                array.set(i, ((BigDecimal) value).doubleValue());
            } else {
                array.set(i, value.toString());
            }
        }
        return array;
    }
}