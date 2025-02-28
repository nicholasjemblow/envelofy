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
import com.vaadin.flow.component.dependency.NpmPackage;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.emblow.envelofy.domain.Transaction;
import org.emblow.envelofy.service.TransactionService;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.emblow.envelofy.domain.Account;

@Tag("spending-trends-chart")
@JsModule("./spending-trends-chart.js")
@NpmPackage(value = "echarts", version = "5.4.0")
@AnonymousAllowed
public class SpendingTrendsChart extends LitTemplate {
    private final TransactionService transactionService;

    public SpendingTrendsChart(TransactionService transactionService) {
        this.transactionService = transactionService;
        // Set sizing via style
        getElement().getStyle().set("display", "block");
        getElement().getStyle().set("height", "400px");
        getElement().getStyle().set("width", "100%");
        refreshData();
    }

   

    private void refreshData() {
        LocalDateTime sixMonthsAgo = LocalDateTime.now().minusMonths(6);
        List<Transaction> transactions = transactionService.getRecentTransactions(
            sixMonthsAgo, 
            LocalDateTime.now()
        );

        // Group transactions by month and envelope
        Map<YearMonth, Map<String, BigDecimal>> monthlyEnvelopeTotals = transactions.stream()
            .collect(Collectors.groupingBy(
                tx -> YearMonth.from(tx.getDate()),
                Collectors.groupingBy(
                    tx -> tx.getEnvelope().getName(),
                    Collectors.reducing(
                        BigDecimal.ZERO,
                        Transaction::getAmount,
                        BigDecimal::add
                    )
                )
            ));

        // Get all unique envelope names
        List<String> envelopes = transactions.stream()
            .map(tx -> tx.getEnvelope().getName())
            .distinct()
            .sorted()
            .collect(Collectors.toList());

        // Build labels array using double quotes
        YearMonth current = YearMonth.now().minusMonths(5);
        StringBuilder labels = new StringBuilder("[");
        for (int i = 0; i <= 5; i++) {
            if (i > 0) labels.append(",");
            labels.append("\"")
                  .append(current.format(DateTimeFormatter.ofPattern("MMM yy")))
                  .append("\"");
            current = current.plusMonths(1);
        }
        labels.append("]");


        // Build series array for each envelope
        StringBuilder series = new StringBuilder("[");
        for (int i = 0; i < envelopes.size(); i++) {
            if (i > 0) series.append(",");
            String envelope = envelopes.get(i);

            series.append("{")
                  .append("\"name\":\"").append(envelope).append("\",")
                  .append("\"type\":\"bar\",")
                  .append("\"stack\":\"total\",")
                  .append("\"data\":[");

            current = YearMonth.now().minusMonths(5);
            for (int j = 0; j <= 5; j++) {
                if (j > 0) series.append(",");
                BigDecimal amount = monthlyEnvelopeTotals
                    .getOrDefault(current, Map.of())
                    .getOrDefault(envelope, BigDecimal.ZERO);
                series.append(amount.doubleValue());
                current = current.plusMonths(1);
            }
            series.append("]}");
        }
        series.append("]");


        // Update chart properties
        getElement().setProperty("labels", labels.toString());
        getElement().setProperty("series", series.toString());
        getElement().setProperty("envelopes", envelopes.toString());
    }
}