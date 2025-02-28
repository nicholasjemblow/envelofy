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
import org.emblow.envelofy.domain.Account;
import org.emblow.envelofy.domain.Transaction;
import org.emblow.envelofy.domain.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Tag("account-balance-chart")
@JsModule("./account-balance-chart.js")
public class AccountBalanceChart extends LitTemplate {

    private final Account account;

    public AccountBalanceChart(Account account) {
        this.account = account;
        // Set sizing via style
        getElement().getStyle().set("display", "block");
        getElement().getStyle().set("height", "400px");
        getElement().getStyle().set("width", "100%");
        refreshData();
    }

    public void refreshData() {
        // Get all transactions for this account in ascending order.
        List<Transaction> transactions = account.getTransactions().stream()
            .sorted(Comparator.comparing(Transaction::getDate))
            .collect(Collectors.toList());

        // We assume account.getBalance() returns the current balance (after all transactions).
        // To compute the balance history, we first compute the “initial balance” before the first transaction.
        BigDecimal currentBalance = account.getBalance();
        BigDecimal totalEffect = transactions.stream()
            .map(tx -> tx.getType() == TransactionType.INCOME ? tx.getAmount() : tx.getAmount().negate())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        // Thus, initialBalance = currentBalance - totalEffect.
        BigDecimal initialBalance = currentBalance.subtract(totalEffect);

        // Build lists for the date labels and balance values.
        List<LocalDate> dates = new ArrayList<>();
        List<BigDecimal> balances = new ArrayList<>();

        if (transactions.isEmpty()) {
            // If there are no transactions, simply use today's date and current balance.
            LocalDate today = LocalDate.now();
            dates.add(today);
            balances.add(currentBalance);
        } else {
            // Add an initial point at the date of the first transaction.
            LocalDate startDate = transactions.get(0).getDate().toLocalDate();
            dates.add(startDate);
            balances.add(initialBalance);
            BigDecimal runningBalance = initialBalance;
            for (Transaction tx : transactions) {
                LocalDate date = tx.getDate().toLocalDate();
                BigDecimal effect = tx.getType() == TransactionType.INCOME ? tx.getAmount() : tx.getAmount().negate();
                runningBalance = runningBalance.add(effect);
                dates.add(date);
                balances.add(runningBalance);
            }
        }

        // Build a JSON array for the labels (formatted dates).
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd");
        StringBuilder labels = new StringBuilder("[");
        for (int i = 0; i < dates.size(); i++) {
            if (i > 0) {
                labels.append(",");
            }
            labels.append("\"").append(dates.get(i).format(formatter)).append("\"");
        }
        labels.append("]");

        // Build a JSON array for the balance values.
        StringBuilder data = new StringBuilder("[");
        for (int i = 0; i < balances.size(); i++) {
            if (i > 0) {
                data.append(",");
            }
            data.append(balances.get(i).toPlainString());
        }
        data.append("]");

        // Pass the JSON arrays as properties to the client-side component.
        getElement().setProperty("labels", labels.toString());
        getElement().setProperty("data", data.toString());
    }
}
