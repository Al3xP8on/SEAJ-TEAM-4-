import { TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { AdminObservabilityComponent } from './observability';
import { ObservabilityService, ObservabilitySnapshot } from './observability.service';

const SNAPSHOT: ObservabilitySnapshot = {
  status: 'CONNECTED',
  message: 'Receiving telemetry.',
  observedAt: '2026-10-19T12:00:00Z',
  windowSeconds: 3600,
  refreshSeconds: 30,
  grafanaUrl: '',
  services: [
    {
      id: 'seaj-app',
      name: 'Trading API',
      reporting: true,
      requestsPerSecond: 10,
      errorPercent: 2,
      p95Millis: 125,
    },
    {
      id: 'seaj-auth-service',
      name: 'Authentication',
      reporting: true,
      requestsPerSecond: 2,
      errorPercent: 0,
      p95Millis: 80,
    },
    {
      id: 'seaj-execution-engine',
      name: 'Execution engine',
      reporting: false,
      requestsPerSecond: null,
      errorPercent: null,
      p95Millis: null,
    },
  ],
  monitors: [{ id: 'otel-collector', name: 'Telemetry pipeline', status: 'UP' }],
  consumerLag: [{ group: 'engine', topic: 'orders', lag: 12 }],
  traffic: [
    { timestamp: '2026-10-19T11:00:00Z', value: 0 },
    { timestamp: '2026-10-19T11:02:00Z', value: 10 },
    { timestamp: '2026-10-19T11:10:00Z', value: 12 },
  ],
  trading: { placed: 15, filled: 12, rejected: 1, tradedValue: 2450.5 },
};

describe('Admin observability', () => {
  const service = { snapshot: vi.fn() };
  beforeEach(async () => {
    service.snapshot.mockReset().mockReturnValue(of(SNAPSHOT));
    await TestBed.configureTestingModule({
      imports: [AdminObservabilityComponent],
      providers: [{ provide: ObservabilityService, useValue: service }],
    }).compileComponents();
  });
  afterEach(() => {
    vi.restoreAllMocks();
    vi.useRealTimers();
  });
  async function render() {
    const fixture = TestBed.createComponent(AdminObservabilityComponent);
    fixture.componentInstance.refresh();
    await fixture.whenStable();
    return {
      fixture,
      component: fixture.componentInstance,
      page: fixture.nativeElement as HTMLElement,
    };
  }

  it('shows service signals, trading activity and Kafka lag with real units', async () => {
    const { component, page } = await render();
    expect(component.requests()).toBe(12);
    expect(component.errorPercent()).toBeCloseTo(1.6667, 3);
    expect(component.latency()).toBe(125);
    expect(page.textContent).toContain('Trading API');
    expect(page.textContent).toContain('No telemetry');
    expect(page.textContent).toContain('$2,450.50');
    expect(page.querySelector('table')?.textContent).toContain('engine');
    expect(page.textContent).toContain('Scrape up');
    expect(page.querySelector('iframe')).toBeNull();
  });

  it('shows missing samples as unknown rather than healthy zeros', async () => {
    service.snapshot.mockReturnValue(
      of({ ...SNAPSHOT, services: [], traffic: [], consumerLag: [], trading: {} }),
    );
    const { component, page } = await render();
    expect(component.requests()).toBeNull();
    expect(component.errorPercent()).toBeNull();
    expect(component.latency()).toBeNull();
    expect(page.querySelector('.signal-cards')?.textContent).toContain('—');
    expect(page.textContent).toContain('Waiting for traffic samples');
    expect(page.textContent).toContain('No lag samples');
  });

  it('breaks chart lines across missing samples and preserves a measured zero', async () => {
    const { component } = await render();
    expect(component.chart()?.line.match(/M/g)).toHaveLength(2);
    expect(component.chart()?.line).toContain('M12,155');
  });

  it('clears old healthy readings if a later refresh fails', async () => {
    const { fixture, component, page } = await render();
    service.snapshot.mockReturnValue(throwError(() => new Error('Offline')));
    component.refresh();
    await fixture.whenStable();
    expect(component.data()).toBeNull();
    expect(page.textContent).toContain('Monitoring is not connected');
    expect(page.querySelector('.signal-cards')).toBeNull();
    expect(page.textContent).toContain('Account management works independently');
  });

  it('shows partial monitoring without hiding usable data', async () => {
    service.snapshot.mockReturnValue(
      of({ ...SNAPSHOT, status: 'PARTIAL', message: 'Lag could not be loaded.' }),
    );
    const { page } = await render();
    expect(page.textContent).toContain('Partial data');
    expect(page.textContent).toContain('Lag could not be loaded');
    expect(page.textContent).toContain('Trading API');
  });

  it('pauses polling in hidden tabs and uses thirty-second intervals when visible', async () => {
    const { fixture } = await render();
    vi.useFakeTimers();
    let hidden = true;
    vi.spyOn(document, 'hidden', 'get').mockImplementation(() => hidden);
    document.dispatchEvent(new Event('visibilitychange'));
    const calls = service.snapshot.mock.calls.length;
    await vi.advanceTimersByTimeAsync(90_000);
    expect(service.snapshot).toHaveBeenCalledTimes(calls);
    hidden = false;
    document.dispatchEvent(new Event('visibilitychange'));
    await vi.advanceTimersByTimeAsync(1);
    expect(service.snapshot).toHaveBeenCalledTimes(calls + 1);
    await vi.advanceTimersByTimeAsync(30_000);
    expect(service.snapshot).toHaveBeenCalledTimes(calls + 2);
    fixture.destroy();
  });

  it('prevents duplicate refresh requests', async () => {
    service.snapshot.mockReturnValue(new Subject());
    const fixture = TestBed.createComponent(AdminObservabilityComponent);
    fixture.componentInstance.refresh();
    fixture.componentInstance.refresh();
    expect(service.snapshot).toHaveBeenCalledTimes(1);
    fixture.destroy();
  });

  it('links to the pulled Grafana dashboards and preserves configured subpaths', async () => {
    service.snapshot.mockReturnValue(
      of({ ...SNAPSHOT, grafanaUrl: 'https://monitoring.example/grafana' }),
    );
    const { component, page } = await render();
    expect(component.grafana(15)).toContain('https://monitoring.example/grafana/d/seaj-platform/');
    expect(component.grafana(15)).toContain('viewPanel=15');
    expect(component.grafana(2, true)).toContain('seaj-stack-health');
    for (const link of page.querySelectorAll<HTMLAnchorElement>('a[target="_blank"]'))
      expect(link.rel).toContain('noopener');
  });

  it('does not create executable links from an invalid Grafana URL', async () => {
    service.snapshot.mockReturnValue(of({ ...SNAPSHOT, grafanaUrl: 'javascript:alert(1)' }));
    const { component } = await render();
    expect(component.grafana()).not.toContain('javascript:');
    expect(component.grafana()).toContain(':8084/d/seaj-platform/');
  });
});
