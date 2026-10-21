import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

export interface ServiceSignal {
  id: string;
  name: string;
  reporting: boolean;
  requestsPerSecond: number | null;
  errorPercent: number | null;
  p95Millis: number | null;
}
export interface ObservabilitySnapshot {
  status: 'CONNECTED' | 'PARTIAL' | 'UNAVAILABLE';
  message: string;
  observedAt: string;
  windowSeconds: number;
  refreshSeconds: number;
  grafanaUrl: string;
  services: ServiceSignal[];
  monitors: { id: string; name: string; status: 'UP' | 'DOWN' | 'UNKNOWN' }[];
  consumerLag: { group: string; topic: string; lag: number }[];
  traffic: { timestamp: string; value: number }[];
  trading: Partial<
    Record<'placed' | 'filled' | 'rejected' | 'cancelled' | 'tradedValue' | 'fillSeconds', number>
  >;
}

@Injectable({ providedIn: 'root' })
export class ObservabilityService {
  private readonly http = inject(HttpClient);
  snapshot() {
    return this.http.get<ObservabilitySnapshot>('/api/v1/admin/observability');
  }
}
