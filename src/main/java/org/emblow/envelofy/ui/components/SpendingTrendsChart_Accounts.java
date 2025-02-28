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
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.emblow.envelofy.domain.Account;
import org.emblow.envelofy.domain.Transaction;
import org.emblow.envelofy.domain.TransactionType;


/**
 *
 * @author Nicholas J Emblow
 */
@Tag("spending-trends-chart-accounts")
@JsModule("./spending-trends-chart-accounts.js")
public class SpendingTrendsChart_Accounts extends LitTemplate {
    private final Account account;

    public SpendingTrendsChart_Accounts(Account account) {
        this.account = account;
        refreshData();
    }

    public void refreshData() {
       // Group and sum expense transactions by month for the last six months
       Map<YearMonth, BigDecimal> monthlySpending = account.getTransactions().stream()
           .filter(tx -> tx.getType() == TransactionType.EXPENSE)
           .filter(tx -> tx.getDate().isAfter(LocalDateTime.now().minusMonths(6)))
           .collect(Collectors.groupingBy(
               tx -> YearMonth.from(tx.getDate()),
               Collectors.reducing(
                   BigDecimal.ZERO,
                   Transaction::getAmount,
                   BigDecimal::add
               )
           ));

       // Sort by month
       TreeMap<YearMonth, BigDecimal> sortedSpending = new TreeMap<>(monthlySpending);

       // Prepare labels and values
       JsonArray labels = Json.createArray();
       JsonArray values = Json.createArray();

       DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM yyyy");
       sortedSpending.forEach((month, amount) -> {
           labels.set(labels.length(), month.format(formatter));
           values.set(values.length(), amount.doubleValue());
       });

       // Create dataset without Chart.js-specific properties
       JsonObject dataset = Json.createObject();
       dataset.put("label", "Monthly Spending");
       dataset.put("data", values);
       // Remove borderColor and tension as they are not used in ECharts
       // dataset.put("borderColor", "rgb(75, 192, 192)");
       // dataset.put("tension", 0.1);

       JsonArray datasets = Json.createArray();
       datasets.set(0, dataset);

       // Create data object
       JsonObject data = Json.createObject();
       data.put("labels", labels);
       data.put("datasets", datasets);

       // Set the chartData property to the data object
       getElement().setProperty("chartData", data.toJson());
   }
}