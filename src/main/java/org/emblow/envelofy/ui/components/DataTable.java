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

import com.fasterxml.jackson.databind.JsonNode;
import com.vaadin.flow.component.littemplate.LitTemplate;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Tag("data-table")
@JsModule("./data-table.js")
@AnonymousAllowed
public class DataTable extends LitTemplate {
    
    public DataTable() {
        // Set sizing via style
        getElement().getStyle().set("display", "block");
        getElement().getStyle().set("width", "100%");
    }

    public void setTableData(String headers, String rows) {
        getElement().setProperty("headers", headers);
        getElement().setProperty("rows", rows);
        getElement().executeJs("this._updateTable()");
    }

    public static DataTable createFromJson(JsonNode tableNode) {
        DataTable table = new DataTable();
        
        try {
            if (tableNode.has("headers") && tableNode.has("rows")) {
                table.setTableData(
                    tableNode.get("headers").toString(),
                    tableNode.get("rows").toString()
                );
            }
        } catch (Exception e) {
            throw new RuntimeException("Error creating table from JSON: " + e.getMessage());
        }
        
        return table;
    }
}