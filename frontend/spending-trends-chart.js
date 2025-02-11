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


import { LitElement, html, css } from 'lit';
import * as echarts from 'echarts';

class SpendingTrendsChart extends LitElement {
    static properties = {
        labels: { type: Array },
        series: { type: Array },
        envelopes: { type: Array }
    };

    constructor() {
        super();
        this.labels = [];
        this.series = [];
        this.envelopes = [];
    }

    firstUpdated() {
        this._renderChart();
    }

    updated(changedProperties) {
        if (changedProperties.has('labels') || 
            changedProperties.has('series') || 
            changedProperties.has('envelopes')) {
            this._renderChart();
        }
    }

    _renderChart() {
        if (!this.shadowRoot) return;

        let container = this.shadowRoot.getElementById('chart');
        if (!container) return;

        let chart = echarts.init(container);
        
        // Use a pleasant color palette
        const colors = [
            '#5470c6', '#91cc75', '#fac858', '#ee6666',
            '#73c0de', '#3ba272', '#fc8452', '#9a60b4'
        ];

        chart.setOption({
            color: colors,
            tooltip: {
                trigger: 'axis',
                axisPointer: {
                    type: 'shadow'
                }
            },
            legend: {
                data: this.envelopes
            },
            grid: {
                left: '3%',
                right: '4%',
                bottom: '3%',
                containLabel: true
            },
            xAxis: {
                type: 'category',
                data: this.labels
            },
            yAxis: {
                type: 'value',
                axisLabel: {
                    formatter: (value) => '$' + value.toFixed(2)
                }
            },
            series: this.series
        });

        // Handle resize
        window.addEventListener('resize', () => {
            chart.resize();
        });
    }

    render() {
        return html`<div id="chart" style="width:100%; height:100%;"></div>`;
    }
}

customElements.define('spending-trends-chart', SpendingTrendsChart);
