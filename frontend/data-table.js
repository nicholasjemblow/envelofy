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


export default class DataTable extends HTMLElement {
    constructor() {
        super();
        this.attachShadow({ mode: 'open' });
    }

    connectedCallback() {
        this._updateTable();
    }

    _updateTable() {
        try {
            const headers = JSON.parse(this.headers || '[]');
            const rows = JSON.parse(this.rows || '[]');
            
            // Create table element
            const table = document.createElement('table');
            table.style.width = '100%';
            table.style.borderCollapse = 'collapse';
            table.style.marginBottom = '1rem';
            
            // Add headers
            const thead = document.createElement('thead');
            const headerRow = document.createElement('tr');
            headers.forEach(header => {
                const th = document.createElement('th');
                th.textContent = header;
                th.style.padding = '0.75rem';
                th.style.borderBottom = '2px solid var(--lumo-contrast-10pct)';
                th.style.textAlign = 'left';
                th.style.fontWeight = 'bold';
                headerRow.appendChild(th);
            });
            thead.appendChild(headerRow);
            table.appendChild(thead);
            
            // Add data rows
            const tbody = document.createElement('tbody');
            rows.forEach(row => {
                const tr = document.createElement('tr');
                row.forEach(cell => {
                    const td = document.createElement('td');
                    td.textContent = cell;
                    td.style.padding = '0.75rem';
                    td.style.borderBottom = '1px solid var(--lumo-contrast-10pct)';
                    tr.appendChild(td);
                });
                tbody.appendChild(tr);
            });
            table.appendChild(tbody);
            
            // Clear and update shadow root
            while (this.shadowRoot.firstChild) {
                this.shadowRoot.removeChild(this.shadowRoot.firstChild);
            }
            this.shadowRoot.appendChild(table);
            
        } catch (e) {
            console.error('Error updating table:', e);
        }
    }

    set headers(value) {
        this._headers = value;
        this._updateTable();
    }

    get headers() {
        return this._headers;
    }

    set rows(value) {
        this._rows = value;
        this._updateTable();
    }

    get rows() {
        return this._rows;
    }
}

customElements.define('data-table', DataTable);