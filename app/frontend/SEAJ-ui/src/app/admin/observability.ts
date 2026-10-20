import { CurrencyPipe, DatePipe, DecimalPipe, DOCUMENT } from '@angular/common';
import { Component, DestroyRef, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  EMPTY,
  catchError,
  exhaustMap,
  finalize,
  fromEvent,
  startWith,
  switchMap,
  timer,
} from 'rxjs';
import { ObservabilityService, ObservabilitySnapshot } from './observability.service';

@Component({
  selector: 'app-admin-observability',
  imports: [CurrencyPipe, DatePipe, DecimalPipe],
  templateUrl: './observability.html',
  styleUrl: './observability.css',
})
export class AdminObservabilityComponent {
  readonly compact = input(false);
  private readonly service = inject(ObservabilityService);
  private readonly document = inject(DOCUMENT);
  private readonly destroy = inject(DestroyRef);
  readonly data = signal<ObservabilitySnapshot | null>(null);
  readonly loading = signal(true);
  readonly refreshing = signal(false);
  readonly error = signal('');
  readonly unavailable = computed(() => !!this.error() || this.data()?.status === 'UNAVAILABLE');
  readonly requests = computed(() => {
    const values =
      this.data()?.services.flatMap((service) =>
        service.requestsPerSecond === null ? [] : [service.requestsPerSecond],
      ) ?? [];
    return values.length ? values.reduce((sum, value) => sum + value, 0) : null;
  });
  readonly errorPercent = computed(() => {
    const services =
      this.data()?.services.filter(
        (service) => service.requestsPerSecond !== null && service.errorPercent !== null,
      ) ?? [];
    const total = services.reduce((sum, service) => sum + service.requestsPerSecond!, 0);
    return total > 0
      ? services.reduce(
          (sum, service) => sum + service.requestsPerSecond! * service.errorPercent!,
          0,
        ) / total
      : null;
  });
  readonly latency = computed(() => {
    const values =
      this.data()?.services.flatMap((service) =>
        service.p95Millis === null ? [] : [service.p95Millis],
      ) ?? [];
    return values.length ? Math.max(...values) : null;
  });
  readonly chart = computed(() => {
    const points = (this.data()?.traffic ?? []).filter((point) => Number.isFinite(point.value));
    if (points.length < 2) return null;
    const first = Date.parse(points[0].timestamp),
      last = Date.parse(points.at(-1)!.timestamp);
    if (last <= first) return null;
    const high = Math.max(...points.map((point) => point.value), 0.01);
    let line = '';
    points.forEach((point, index) => {
      const x = 12 + ((Date.parse(point.timestamp) - first) / (last - first)) * 676;
      const y = 155 - (point.value / high) * 125;
      const gap =
        index === 0 ||
        Date.parse(point.timestamp) - Date.parse(points[index - 1].timestamp) > 180_000;
      line += `${gap ? 'M' : 'L'}${x},${y} `;
    });
    return { line, first: points[0].timestamp, last: points.at(-1)!.timestamp, high };
  });
  readonly grafanaBase = computed(() => {
    const configured = this.data()?.grafanaUrl;
    const fallback = new URL(this.document.location?.href ?? 'http://localhost:8085');
    fallback.port = '8084';
    fallback.pathname = '/';
    fallback.search = '';
    fallback.hash = '';
    try {
      const url = configured ? new URL(configured) : fallback;
      return ['http:', 'https:'].includes(url.protocol) && !url.username && !url.password
        ? url.href.replace(/\/$/, '')
        : fallback.origin;
    } catch {
      return fallback.origin;
    }
  });

  constructor() {
    fromEvent(this.document, 'visibilitychange')
      .pipe(
        startWith(null),
        switchMap(() =>
          this.document.hidden ? EMPTY : timer(0, 30_000).pipe(exhaustMap(() => this.request())),
        ),
        takeUntilDestroyed(this.destroy),
      )
      .subscribe();
  }

  refresh(): void {
    if (this.refreshing()) return;
    this.request().pipe(takeUntilDestroyed(this.destroy)).subscribe();
  }

  private request() {
    if (this.refreshing()) return EMPTY;
    this.refreshing.set(true);
    return this.service.snapshot().pipe(
      exhaustMap((snapshot) => {
        this.data.set(snapshot);
        this.error.set('');
        return EMPTY;
      }),
      catchError(() => {
        this.data.set(null);
        this.error.set(
          'Monitoring could not be loaded. Your account controls are still available.',
        );
        return EMPTY;
      }),
      finalize(() => {
        this.loading.set(false);
        this.refreshing.set(false);
      }),
    );
  }

  grafana(panel?: number, stack = false): string {
    return `${this.grafanaBase()}/d/${stack ? 'seaj-stack-health/observability-stack-health' : 'seaj-platform/seaj-trading-platform'}?from=now-1h&to=now${panel ? `&viewPanel=${panel}` : ''}`;
  }
}
