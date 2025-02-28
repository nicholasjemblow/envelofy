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

import { LitElement, html } from 'lit-element';
import * as echarts from 'echarts';

class SpendingCategoryChart extends LitElement {
    static get properties() {
        return {
            chartData: { type: Object }
        };
    }

    constructor() {
        super();
        this.chart = null;
    }

    render() {
        return html`<div style="width: 100%; height: 100%;"></div>`;
    }

    firstUpdated() {
        const chartDiv = this.shadowRoot.querySelector('div');
        this.chart = echarts.init(chartDiv);
        console.log('SpendingCategoryChart - firstUpdated, chartData:', this.chartData);
        this._updateChart();
    }

    updated(changedProperties) {
        if (changedProperties.has('chartData') && this.chart) {
            console.log('SpendingCategoryChart - updated, chartData:', this.chartData);
            this._updateChart();
        }
    }

    disconnectedCallback() {
        if (this.chart) {
            this.chart.dispose();
            this.chart = null;
        }
        super.disconnectedCallback();
    }

    _updateChart() {
        if (!this.chart || !this.chartData) {
            console.log('SpendingCategoryChart - Skipping update: chart or chartData not ready', {
                chart: !!this.chart,
                chartData: this.chartData
            });
            return;
        }

        // Handle case where chartData is a JSON string
        let data = this.chartData;
        if (typeof this.chartData === 'string') {
            try {
                data = JSON.parse(this.chartData);
                console.log('SpendingCategoryChart - Parsed string chartData:', data);
            } catch (e) {
                console.error('SpendingCategoryChart - Failed to parse chartData string:', this.chartData, e);
                return;
            }
        }

        if (!data.datasets || !Array.isArray(data.datasets) || data.datasets.length === 0) {
            console.error('SpendingCategoryChart - Invalid chartData: datasets issue', {
                hasDatasets: !!data.datasets,
                isArray: Array.isArray(data.datasets),
                length: data.datasets ? data.datasets.length : 'N/A',
                chartData: data
            });
            return;
        }

        const labels = data.labels || [];
        const dataset = data.datasets[0] || {};
        const values = dataset.data || [];
        const colors = dataset.backgroundColor || [];

        if (labels.length === 0 || values.length === 0) {
            console.warn('SpendingCategoryChart - No data to display', { labels, values });
            return;
        }

        const pieData = labels.map((label, index) => ({
            name: label,
            value: values[index] || 0
        }));

        const option = {
            tooltip: {
                trigger: 'item',
                formatter: function(params) {
                    const value = params.value || 0;
                    const formattedValue = new Intl.NumberFormat('en-US', {
                        style: 'currency',
                        currency: 'USD'
                    }).format(value);
                    return `${params.name}: ${formattedValue}`;
                }
            },
            legend: {
                orient: 'vertical',
                left: 'right'
            },
            series: [{
                type: 'pie',
                data: pieData,
                color: colors
            }]
        };

        this.chart.setOption(option);
    }
}

customElements.define('spending-category-chart', SpendingCategoryChart);