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

class SpendingTrendsChart_Accounts extends LitElement {
    static get properties() {
        return {
            chartData: { type: Object }
        };
    }

    constructor() {
        super();
        this.chart = null;
        this.chartDiv = null;
    }

    render() {
        return html`<div style="width: 100%; height: 100%;"></div>`;
    }

    firstUpdated() {
        this.chartDiv = this.shadowRoot.querySelector('div');
        this.chart = echarts.init(this.chartDiv);
        console.log('SpendingTrendsChart_Accounts - firstUpdated, chartData:', this.chartData);
        this._updateChart();
    }

    updated(changedProperties) {
        if (changedProperties.has('chartData') && this.chart) {
            console.log('SpendingTrendsChart_Accounts - updated, chartData:', this.chartData);
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
            console.log('SpendingTrendsChart_Accounts - Skipping update: chart or chartData not ready', {
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
                console.log('SpendingTrendsChart_Accounts - Parsed string chartData:', data);
            } catch (e) {
                console.error('SpendingTrendsChart_Accounts - Failed to parse chartData string:', this.chartData, e);
                return;
            }
        }

        if (!data.datasets || !Array.isArray(data.datasets) || data.datasets.length === 0) {
            console.error('SpendingTrendsChart_Accounts - Invalid chartData: datasets issue', {
                hasDatasets: !!data.datasets,
                isArray: Array.isArray(data.datasets),
                length: data.datasets ? data.datasets.length : 'N/A',
                chartData: data
            });
            return;
        }

        const labels = data.labels || [];
        const series = data.datasets.map(dataset => {
            if (!dataset.data || !dataset.label) {
                console.warn('SpendingTrendsChart_Accounts - Dataset missing required fields', dataset);
            }
            return {
                name: dataset.label || 'Unnamed Series',
                type: 'line',
                data: dataset.data || []
            };
        });

        if (labels.length === 0 || series.every(s => s.data.length === 0)) {
            console.warn('SpendingTrendsChart_Accounts - No data to display', { labels, series });
            return;
        }

        const option = {
            tooltip: {
                trigger: 'axis',
                formatter: function(params) {
                    let result = params[0].name + '<br/>';
                    params.forEach(param => {
                        const value = new Intl.NumberFormat('en-US', {
                            style: 'currency',
                            currency: 'USD'
                        }).format(param.value || 0);
                        result += param.marker + param.seriesName + ': ' + value + '<br/>';
                    });
                    return result;
                }
            },
            xAxis: {
                type: 'category',
                data: labels
            },
            yAxis: {
                type: 'value',
                axisLabel: {
                    formatter: function(value) {
                        return new Intl.NumberFormat('en-US', {
                            style: 'currency',
                            currency: 'USD',
                            minimumFractionDigits: 0,
                            maximumFractionDigits: 0
                        }).format(value);
                    }
                }
            },
            series: series
        };

        this.chart.setOption(option);
    }
}

customElements.define('spending-trends-chart-accounts', SpendingTrendsChart_Accounts);