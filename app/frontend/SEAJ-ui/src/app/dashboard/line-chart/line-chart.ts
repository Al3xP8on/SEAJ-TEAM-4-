import { Component, computed, input, linkedSignal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';

export interface LinePoint {
  time: string;
  value: number;
}

const WIDTH = 600;
const HEIGHT = 180;
const PADDING = 12;

let nextId = 0;

/**
 * An accessible SVG line chart: a text description, arrow keys to step through values (announced
 * politely), and the same reading on hover. Used by the performance card and the stock pop-up.
 */
@Component({
  selector: 'app-line-chart',
  imports: [CurrencyPipe, DatePipe],
  templateUrl: './line-chart.html',
  styleUrl: './line-chart.css',
})
export class LineChartComponent {
  readonly points = input.required<LinePoint[]>();
  /** What the line shows, e.g. "Holdings value over 1M, from $300.00 to $325.00" */
  readonly description = input.required<string>();
  readonly dateFormat = input('MMM d');
  /** Draws the line in the falling colour */
  readonly loss = input(false);
  /** Heading for the value column of the data table, e.g. "Close" */
  readonly valueLabel = input('Value');

  readonly width = WIDTH;
  readonly height = HEIGHT;
  readonly readoutId = `line-chart-readout-${nextId++}`;

  /** The point being read, cleared whenever new data arrives */
  readonly inspectIndex = linkedSignal<LinePoint[], number | null>({
    source: this.points,
    computation: () => null,
  });

  readonly range = computed(() => {
    const values = this.points().map((p) => p.value);
    return { high: Math.max(...values), low: Math.min(...values) };
  });

  readonly plot = computed(() => {
    const points = this.points();
    if (points.length < 2) {
      return null;
    }
    const { high, low } = this.range();
    const span = high - low || 1;
    const coords = points.map((p, i) => ({
      x: (i / (points.length - 1)) * WIDTH,
      y: PADDING + (1 - (p.value - low) / span) * (HEIGHT - 2 * PADDING),
    }));
    const line = coords.map((c) => `${c.x.toFixed(1)},${c.y.toFixed(1)}`).join(' ');
    return {
      coords,
      line,
      area: `M0,${HEIGHT} L${line.replaceAll(' ', ' L')} L${WIDTH},${HEIGHT} Z`,
    };
  });

  readonly inspected = computed(() => {
    const index = this.inspectIndex();
    return index === null ? null : (this.points()[index] ?? null);
  });

  readonly marker = computed(() => {
    const index = this.inspectIndex();
    return index === null ? null : (this.plot()?.coords[index] ?? null);
  });

  /** Arrow keys step through points, Home and End jump to the ends, Escape clears */
  onKey(event: KeyboardEvent): void {
    const last = this.points().length - 1;
    if (last < 0) {
      return;
    }
    const current = this.inspectIndex() ?? last;
    const next: Record<string, number | null> = {
      ArrowLeft: Math.max(0, current - 1),
      ArrowRight: Math.min(last, current + 1),
      Home: 0,
      End: last,
      Escape: null,
    };
    if (event.key in next) {
      event.preventDefault();
      this.inspectIndex.set(next[event.key]);
    }
  }

  onPointer(event: PointerEvent): void {
    const rect = (event.currentTarget as SVGElement).getBoundingClientRect();
    const last = this.points().length - 1;
    if (last < 1 || !rect.width) {
      return;
    }
    const ratio = Math.min(1, Math.max(0, (event.clientX - rect.left) / rect.width));
    this.inspectIndex.set(Math.round(ratio * last));
  }

  clearInspect(): void {
    this.inspectIndex.set(null);
  }
}
