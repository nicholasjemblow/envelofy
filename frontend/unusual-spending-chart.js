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


import * as echarts from 'echarts';

// Export the module directly without using customElements.define
export default class UnusualSpendingChart extends HTMLElement {
    constructor() {
        super();
        this.attachShadow({ mode: 'open' });
        this.chart = null;
    }

    connectedCallback() {
        const chartDiv = document.createElement('div');
        chartDiv.style.width = '100%';
        chartDiv.style.height = '100%';
        this.shadowRoot.appendChild(chartDiv);
        
        this.chart = echarts.init(chartDiv);
        this._updateChart();
    }

    disconnectedCallback() {
        if (this.chart) {
            this.chart.dispose();
            this.chart = null;
        }
    }

    _updateChart() {
        if (!this.chart) return;

        try {
            const labels = JSON.parse(this.labels || '[]');
            const data = JSON.parse(this.data || '[]');

            const option = {
                tooltip: {
                    trigger: 'axis'
                },
                xAxis: {
                    type: 'category',
                    data: labels
                },
                yAxis: {
                    type: 'value',
                    name: 'Confidence (%)',
                    max: 100
                },
                series: [{
                    type: 'bar',
                    data: data,
                    itemStyle: {
                        color: '#f56c6c'
                    }
                }]
            };
            
            this.chart.setOption(option);
        } catch (e) {
            console.error('Error updating chart:', e);
        }
    }

    set labels(value) {
        this._labels = value;
        this._updateChart();
    }

    get labels() {
        return this._labels;
    }

    set data(value) {
        this._data = value;
        this._updateChart();
    }

    get data() {
        return this._data;
    }
}
customElements.define('unusual-spending-chart', UnusualSpendingChart);
