import {
  Component,
  DestroyRef,
  ElementRef,
  afterRenderEffect,
  computed,
  inject,
  input,
  viewChild,
} from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { ArcElement, Chart, DoughnutController, Tooltip } from 'chart.js';

Chart.register(DoughnutController, ArcElement, Tooltip);

export interface AllocationSlice {
  label: string;
  value: number;
}

// Brand green first, then companions that differ in lightness as well as hue
const SLICE_COLOURS = ['#256342', '#4a926a', '#a9d0b9', '#c98a1b', '#5a6b61', '#d7e1da'];

/** Doughnut of how the portfolio splits across holdings and cash, with a text legend */
@Component({
  selector: 'app-allocation-chart',
  imports: [DecimalPipe],
  templateUrl: './allocation-chart.html',
  styleUrl: './allocation-chart.css',
})
export class AllocationChartComponent {
  readonly slices = input.required<AllocationSlice[]>();

  private readonly canvas = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');
  private chart: Chart<'doughnut'> | null = null;

  readonly legend = computed(() => {
    const total = this.slices().reduce((sum, s) => sum + s.value, 0);
    return this.slices().map((slice, i) => ({
      ...slice,
      colour: SLICE_COLOURS[i % SLICE_COLOURS.length],
      percent: total > 0 ? (slice.value / total) * 100 : 0,
    }));
  });

  constructor() {
    afterRenderEffect(() => this.draw(this.legend()));
    inject(DestroyRef).onDestroy(() => this.chart?.destroy());
  }

  private draw(legend: ReturnType<typeof this.legend>): void {
    const data = {
      labels: legend.map((s) => s.label),
      datasets: [
        {
          data: legend.map((s) => s.value),
          backgroundColor: legend.map((s) => s.colour),
          borderWidth: 0,
        },
      ],
    };

    if (this.chart) {
      // Polling refreshes the data often; update in place rather than redrawing from scratch
      this.chart.data = data;
      this.chart.update('none');
      return;
    }

    // No 2D context outside a real browser (e.g. unit tests): the legend still carries the data
    const context = this.canvas().nativeElement.getContext('2d');
    if (!context) {
      return;
    }
    this.chart = new Chart(context, {
      type: 'doughnut',
      data,
      options: {
        animation: false,
        responsive: true,
        maintainAspectRatio: false,
        cutout: '62%',
        plugins: { legend: { display: false } },
      },
    });
  }
}
