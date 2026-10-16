import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { MarketChart } from '../../orders/orders.models';
import { DashboardService } from '../dashboard.service';
import { PerformanceChartComponent } from './performance-chart';
import { PerformanceHolding } from './performance-series';

const daily = (symbol: string, closes: number[]): MarketChart => ({
  symbol,
  feed: 'iex',
  timeframe: '1Day',
  bars: closes.map((close, i) => ({
    timestamp: `2026-10-${String(10 + i).padStart(2, '0')}T04:00:00Z`,
    open: close,
    high: close,
    low: close,
    close,
    volume: 1,
  })),
  message: '',
});

const HISTORY: Record<string, MarketChart> = {
  AAPL: daily('AAPL', [100, 90, 120]),
  MSFT: daily('MSFT', [200, 210, 205]),
};

describe('PerformanceChartComponent', () => {
  const dashboard = { bars: vi.fn((symbol: string) => of(HISTORY[symbol] ?? null)) };

  async function render(holdings: PerformanceHolding[]) {
    const fixture = TestBed.createComponent(PerformanceChartComponent);
    fixture.componentRef.setInput('holdings', holdings);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    const text = (selector: string) =>
      page.querySelector(selector)?.textContent?.replace(/\s+/g, ' ').trim();
    return { fixture, page, text };
  }

  beforeEach(() => {
    dashboard.bars.mockClear();
    TestBed.configureTestingModule({
      imports: [PerformanceChartComponent],
      providers: [{ provide: DashboardService, useValue: dashboard }],
    });
  });

  // 1 AAPL + 1 MSFT: 300, 300, 325
  const HOLDINGS = [
    { symbol: 'AAPL', quantity: 1 },
    { symbol: 'MSFT', quantity: 1 },
  ];

  it('shows the change over the period with the high and low', async () => {
    const { text } = await render(HOLDINGS);

    expect(text('.change')).toBe('▲ +$25.00 (+8.33%) over 1M');
    expect(text('.readout')).toBe('High $325.00 · Low $300.00');
    expect(dashboard.bars).toHaveBeenCalledWith('AAPL', '1Day');
  });

  it('describes the chart for screen readers', async () => {
    const { page } = await render(HOLDINGS);

    expect(page.querySelector('svg')?.getAttribute('aria-label')).toBe(
      'Holdings value over 1M, from $300.00 to $325.00. Use the arrow keys to read values.',
    );
  });

  it('reads out values with the keyboard', async () => {
    const { fixture, page, text } = await render(HOLDINGS);
    const svg = page.querySelector('svg')!;

    svg.dispatchEvent(new KeyboardEvent('keydown', { key: 'Home' }));
    await fixture.whenStable();
    expect(text('.readout')).toContain('$300.00');

    svg.dispatchEvent(new KeyboardEvent('keydown', { key: 'End' }));
    await fixture.whenStable();
    expect(text('.readout')).toContain('$325.00');
    expect(page.querySelector('circle.dot')).not.toBeNull();

    svg.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    await fixture.whenStable();
    expect(text('.readout')).toBe('High $325.00 · Low $300.00');
  });

  it('loads minute bars when switching to 1D', async () => {
    const { fixture, page } = await render(HOLDINGS);
    const oneDay = Array.from(page.querySelectorAll('.periods button')).find(
      (b) => b.textContent?.trim() === '1D',
    )!;

    (oneDay as HTMLButtonElement).click();
    await fixture.whenStable();

    expect(dashboard.bars).toHaveBeenCalledWith('AAPL', '1Min');
    expect(oneDay.getAttribute('aria-pressed')).toBe('true');
  });

  it('does not reload when the dashboard refreshes with the same holdings', async () => {
    const { fixture } = await render(HOLDINGS);
    const calls = dashboard.bars.mock.calls.length;

    fixture.componentRef.setInput(
      'holdings',
      HOLDINGS.map((h) => ({ ...h })),
    );
    await fixture.whenStable();

    expect(dashboard.bars.mock.calls.length).toBe(calls);
  });

  it('names holdings without price history', async () => {
    const { text } = await render([...HOLDINGS, { symbol: 'XYZ', quantity: 3 }]);

    expect(text('.note')).toBe('Not included (no price history): XYZ');
  });

  it('explains an empty portfolio without requesting history', async () => {
    const { text } = await render([]);

    expect(text('.empty')).toContain("don't hold any positions");
    expect(dashboard.bars).not.toHaveBeenCalled();
  });
});
