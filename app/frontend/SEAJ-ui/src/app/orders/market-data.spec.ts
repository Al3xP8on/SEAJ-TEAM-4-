import { TestBed, ComponentFixture } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import {
  Instrument,
  MarketBar,
  MarketChart,
  MarketOverview,
  MarketSnapshot,
} from './orders.models';
import { MarketDataComponent } from './market-data';
import { OrdersService } from './orders.service';

const instruments: Instrument[] = [
  { symbol: 'AAPL', name: 'Apple', assetClass: 'Equity', currency: 'USD', tradable: true },
  { symbol: 'MSFT', name: 'Microsoft', assetClass: 'Equity', currency: 'USD', tradable: true },
  { symbol: 'GLD', name: 'Gold ETF', assetClass: 'Commodity', currency: 'USD', tradable: true },
  { symbol: 'UST10', name: 'Treasury', assetClass: 'Bond', currency: 'USD', tradable: true },
];
const bar = (timestamp: string, close: number): MarketBar => ({
  timestamp,
  close,
  open: close,
  high: close + 2,
  low: close - 2,
  volume: 10000,
});
const snapshot = (symbol: string, price: number, gain: number | null): MarketSnapshot => ({
  symbol,
  price,
  changePercent: gain,
  previousClose: 100,
  timestamp: '2026-10-14T15:02:00Z',
  dailyBar: bar('2026-10-14T04:00:00Z', price),
  quote: {
    symbol,
    bidPrice: price - 0.1,
    askPrice: price + 0.1,
    lastPrice: price,
    priceTimeStamp: '2026-10-14T15:01:59Z',
  },
});
const overview: MarketOverview = {
  configured: true,
  feed: 'iex',
  updatedAt: '2026-10-14T15:02:01Z',
  message: '',
  snapshots: [snapshot('AAPL', 110, 10), snapshot('MSFT', 220, 20), snapshot('GLD', 95, -5)],
  unsupportedSymbols: ['UST10'],
};

describe('MarketDataComponent with Alpaca', () => {
  const service = { marketOverview: vi.fn(), marketBars: vi.fn() };
  beforeEach(async () => {
    service.marketOverview.mockReset().mockReturnValue(of(overview));
    service.marketBars
      .mockReset()
      .mockImplementation((symbol: string, timeframe: '1Min' | '1Day') =>
        of({
          symbol,
          timeframe,
          feed: 'iex',
          message: '',
          bars:
            timeframe === '1Min'
              ? [bar('2026-10-14T15:00:00Z', 102), bar('2026-10-14T15:01:00Z', 103)]
              : [bar('2026-10-13T04:00:00Z', 100), bar('2026-10-14T04:00:00Z', 105)],
        }),
      );
    await TestBed.configureTestingModule({
      imports: [MarketDataComponent],
      providers: [{ provide: OrdersService, useValue: service }],
    }).compileComponents();
  });
  async function settle(fixture: ComponentFixture<MarketDataComponent>) {
    for (let turn = 0; turn < 3; turn++) {
      await new Promise((resolve) => setTimeout(resolve, 0));
      await fixture.whenStable();
    }
  }
  async function render(symbol = '') {
    const fixture = TestBed.createComponent(MarketDataComponent);
    fixture.componentRef.setInput('instruments', instruments);
    fixture.componentRef.setInput('selectedSymbol', symbol);
    fixture.detectChanges();
    await settle(fixture);
    return {
      fixture,
      component: fixture.componentInstance,
      page: fixture.nativeElement as HTMLElement,
    };
  }

  it('ranks real snapshot changes against previous close and labels the feed', async () => {
    const { component, page } = await render();
    expect(component.leaders().map((entry) => entry.snapshot.symbol)).toEqual([
      'MSFT',
      'AAPL',
      'GLD',
    ]);
    expect(page.querySelector('.leaders')?.textContent).toContain('+20.00%');
    expect(page.querySelector('.negative')?.textContent).toContain('-5.00%');
    expect(page.querySelector('.market-overview .asset-class')?.textContent).toContain(
      'Alpaca · IEX',
    );
    const localDate = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short' }).format(
      new Date(overview.snapshots[0].timestamp),
    );
    expect(page.querySelector('.data-date')?.textContent).toContain(localDate);
    expect(service.marketBars).not.toHaveBeenCalled();
  });
  it('excludes missing previous closes and prices from older trading dates', async () => {
    service.marketOverview.mockReturnValue(
      of({
        ...overview,
        snapshots: [
          snapshot('AAPL', 110, 10),
          snapshot('MSFT', 220, null),
          { ...snapshot('GLD', 95, 30), timestamp: '2026-10-13T15:00:00Z' },
        ],
      }),
    );
    const { component } = await render();
    expect(component.leaders().map((entry) => entry.snapshot.symbol)).toEqual(['AAPL']);
  });
  it('loads an intraday chart and adds the latest trade to the recorded minute closes', async () => {
    const { component, page } = await render('AAPL');
    expect(service.marketBars).toHaveBeenCalledWith('AAPL', '1Min');
    expect(page.querySelector('.close-price')?.textContent).toContain('$110.00');
    expect(page.querySelectorAll('.price-point')).toHaveLength(1);
    expect(component.chart()?.points).toHaveLength(3);
    expect(component.chart()?.points.at(-1)?.value).toBe(110);
    expect(page.querySelector('.quote-note')?.textContent).toContain('Bid $109.90');
    expect(page.querySelector('.quote-note')?.textContent).toContain('Ask $110.10');
  });
  afterEach(() => {
    vi.restoreAllMocks();
    vi.useRealTimers();
  });
  it('keeps a single interactive marker for a full session of history', async () => {
    const start = Date.parse('2026-10-14T08:00:00Z');
    const bars = Array.from({ length: 600 }, (_, index) =>
      bar(new Date(start + index * 60_000).toISOString(), 100 + index / 100),
    );
    service.marketBars.mockReturnValue(
      of({ symbol: 'AAPL', feed: 'iex', timeframe: '1Min', message: '', bars }),
    );
    service.marketOverview.mockReturnValue(
      of({
        ...overview,
        snapshots: [{ ...snapshot('AAPL', 110, 10), timestamp: '2026-10-14T18:00:00Z' }],
      }),
    );
    const { fixture, component, page } = await render('AAPL');
    expect(component.chart()?.points).toHaveLength(601);
    expect(page.querySelectorAll('.price-point')).toHaveLength(1);
    const svg = page.querySelector('svg')!;
    vi.spyOn(svg, 'getBoundingClientRect').mockReturnValue({ left: 0, width: 500 } as DOMRect);
    svg.dispatchEvent(new MouseEvent('pointermove', { clientX: 12, bubbles: true }));
    await fixture.whenStable();
    expect(component.inspectedPoint()?.value).toBe(100);
    expect(page.querySelector('#chart-value')?.textContent).toContain('$100.00');
    svg.dispatchEvent(new KeyboardEvent('keydown', { key: 'End', bubbles: true }));
    await fixture.whenStable();
    expect(component.inspectedPoint()?.value).toBe(110);
    expect(page.querySelectorAll('.price-point')).toHaveLength(1);
  });
  it('pauses all market polling while hidden and resumes with ten-second snapshots', async () => {
    const { fixture } = await render('AAPL');
    vi.useFakeTimers();
    let hidden = true;
    vi.spyOn(document, 'hidden', 'get').mockImplementation(() => hidden);
    document.dispatchEvent(new Event('visibilitychange'));
    const snapshots = service.marketOverview.mock.calls.length;
    const charts = service.marketBars.mock.calls.length;
    await vi.advanceTimersByTimeAsync(70_000);
    expect(service.marketOverview).toHaveBeenCalledTimes(snapshots);
    expect(service.marketBars).toHaveBeenCalledTimes(charts);
    hidden = false;
    document.dispatchEvent(new Event('visibilitychange'));
    await vi.advanceTimersByTimeAsync(1);
    expect(service.marketOverview).toHaveBeenCalledTimes(snapshots + 1);
    await vi.advanceTimersByTimeAsync(9_998);
    expect(service.marketOverview).toHaveBeenCalledTimes(snapshots + 1);
    await vi.advanceTimersByTimeAsync(1);
    expect(service.marketOverview).toHaveBeenCalledTimes(snapshots + 2);
    fixture.destroy();
  });
  it('ranks the same New York trading date together across UTC midnight', async () => {
    service.marketOverview.mockReturnValue(
      of({
        ...overview,
        snapshots: [
          { ...snapshot('AAPL', 110, 10), timestamp: '2026-10-14T23:30:00Z' },
          { ...snapshot('MSFT', 220, 20), timestamp: '2026-10-15T00:30:00Z' },
        ],
      }),
    );
    const { component } = await render();
    expect(component.leaders().map((entry) => entry.snapshot.symbol)).toEqual(['MSFT', 'AAPL']);
  });
  it('switches to daily history with the one-month chart control', async () => {
    const { fixture, component, page } = await render('AAPL');
    page.querySelectorAll<HTMLButtonElement>('.chart-periods button')[2].click();
    await settle(fixture);
    expect(service.marketBars).toHaveBeenLastCalledWith('AAPL', '1Day');
    expect(component.chart()?.points).toHaveLength(2);
    expect(page.querySelector('[aria-pressed="true"]')?.textContent?.trim()).toBe('1M');
  });
  it('keeps yesterday visible with a day divider and compresses the overnight gap', async () => {
    service.marketBars.mockReturnValue(
      of({
        symbol: 'AAPL',
        feed: 'iex',
        timeframe: '1Min',
        message: '',
        bars: [
          bar('2026-10-12T15:00:00Z', 98),
          bar('2026-10-13T19:59:00Z', 99),
          bar('2026-10-14T15:00:00Z', 101),
          bar('2026-10-14T15:01:00Z', 102),
        ],
      }),
    );
    const { component, page } = await render('AAPL');
    const plot = component.chart()!;
    expect(plot.points).toHaveLength(4);
    expect(plot.first).toBe('2026-10-13T19:59:00Z');
    expect(plot.boundaries).toEqual([
      { x: (plot.points[0].x + plot.points[1].x) / 2, previous: '2026-10-13', next: '2026-10-14' },
    ]);
    expect(plot.points[2].x - plot.points[1].x).toBeCloseTo(plot.points[3].x - plot.points[2].x);
    expect(plot.points[1].x - plot.points[0].x).toBeCloseTo(
      15 * (plot.points[2].x - plot.points[1].x),
    );
    expect(page.querySelectorAll('.session-divider')).toHaveLength(1);
    expect(page.querySelector('.session-divider title')?.textContent).toContain('End of 13 Oct');
    expect(
      [...page.querySelectorAll('.session-label')].map((label) => label.textContent?.trim()),
    ).toEqual(['13 Oct', '14 Oct']);
    expect(page.querySelector('.close-legend')?.textContent).toContain('Previous close · $100.00');
    expect(Number(page.querySelector('.previous-close')?.getAttribute('y1'))).toBe(
      plot.previousClose!.y,
    );
  });
  it('shows five trading days for 1W and reuses the same daily request for 1M', async () => {
    const dailyBars = Array.from({ length: 49 }, (_, index) =>
      bar(new Date(Date.UTC(2026, 8, 1 + index, 4)).toISOString(), 100 + index),
    ).filter((entry) => ![0, 6].includes(new Date(entry.timestamp).getUTCDay()));
    service.marketBars.mockImplementation((symbol: string, timeframe: '1Min' | '1Day') =>
      of({
        symbol,
        timeframe,
        feed: 'iex',
        message: '',
        bars: timeframe === '1Day' ? dailyBars : [],
      }),
    );
    const { fixture, component, page } = await render('AAPL');
    page.querySelectorAll<HTMLButtonElement>('.chart-periods button')[1].click();
    await settle(fixture);
    expect(service.marketBars).toHaveBeenLastCalledWith('AAPL', '1Day');
    expect(component.chart()?.points.map((point) => point.timestamp)).toEqual(
      dailyBars.slice(-5).map((entry) => entry.timestamp),
    );
    expect(page.querySelector('[aria-pressed="true"]')?.textContent?.trim()).toBe('1W');
    expect(page.querySelector('figcaption')?.textContent).toContain(
      'Last 5 available trading days.',
    );
    expect(page.querySelector('.session-divider')).toBeNull();
    expect(page.querySelector('.previous-close')).toBeNull();
    const calls = service.marketBars.mock.calls.length;
    page.querySelectorAll<HTMLButtonElement>('.chart-periods button')[2].click();
    await settle(fixture);
    expect(component.chart()?.points).toHaveLength(30);
    expect(service.marketBars).toHaveBeenCalledTimes(calls);
    page.querySelectorAll<HTMLButtonElement>('.chart-periods button')[1].click();
    await settle(fixture);
    expect(component.chart()?.points).toHaveLength(5);
    expect(service.marketBars).toHaveBeenCalledTimes(calls);
  });
  it('keeps the previous session when the first trade of a new day arrives before minute history', async () => {
    const updates = new Subject<MarketOverview>();
    service.marketOverview.mockReturnValue(updates);
    const { fixture, component, page } = await render('AAPL');
    updates.next(overview);
    await settle(fixture);
    const calls = service.marketBars.mock.calls.length;
    updates.next({
      ...overview,
      snapshots: [
        { ...snapshot('AAPL', 112, 1.82), previousClose: 110, timestamp: '2026-10-15T13:30:01Z' },
      ],
    });
    await settle(fixture);
    expect(component.chart()?.first).toBe('2026-10-14T15:00:00Z');
    expect(component.chart()?.points.at(-1)?.value).toBe(112);
    expect(component.chart()?.boundaries).toHaveLength(1);
    expect(component.chart()?.boundaries[0].next).toBe('2026-10-15');
    expect(page.querySelector('.close-legend')?.textContent).toContain('$110.00');
    expect(service.marketBars).toHaveBeenCalledTimes(calls);
  });
  it('uses New York dates for dividers instead of splitting at UTC midnight', async () => {
    service.marketBars.mockReturnValue(
      of({
        symbol: 'AAPL',
        timeframe: '1Min',
        feed: 'iex',
        message: '',
        bars: [bar('2026-10-14T23:30:00Z', 100), bar('2026-10-15T00:01:00Z', 101)],
      }),
    );
    service.marketOverview.mockReturnValue(
      of({
        ...overview,
        snapshots: [{ ...snapshot('AAPL', 102, 2), timestamp: '2026-10-15T00:02:00Z' }],
      }),
    );
    const { component, page } = await render('AAPL');
    expect(component.chart()?.points).toHaveLength(3);
    expect(component.chart()?.boundaries).toHaveLength(0);
    expect(page.querySelectorAll('.session-label')).toHaveLength(1);
    expect(page.querySelector('.session-label')?.textContent).toContain('14 Oct');
  });
  it('separates Friday and Monday without inventing weekend prices', async () => {
    service.marketBars.mockReturnValue(
      of({
        symbol: 'AAPL',
        timeframe: '1Min',
        feed: 'iex',
        message: '',
        bars: [bar('2026-10-09T19:59:00Z', 100), bar('2026-10-12T13:30:00Z', 103)],
      }),
    );
    service.marketOverview.mockReturnValue(
      of({
        ...overview,
        snapshots: [{ ...snapshot('AAPL', 104, 4), timestamp: '2026-10-12T13:31:00Z' }],
      }),
    );
    const { component } = await render('AAPL');
    expect(component.chart()?.sessions.map((session) => session.date)).toEqual([
      '2026-10-09',
      '2026-10-12',
    ]);
    expect(component.chart()?.boundaries).toHaveLength(1);
    expect(component.chart()?.points).toHaveLength(3);
  });
  it('fits the previous close in the price scale and omits it when unavailable', async () => {
    const updates = new Subject<MarketOverview>();
    service.marketOverview.mockReturnValue(updates);
    const { fixture, component, page } = await render('AAPL');
    updates.next({
      ...overview,
      snapshots: [{ ...snapshot('AAPL', 110, 120), previousClose: 50 }],
    });
    await settle(fixture);
    expect(component.chart()?.low).toBe(50);
    expect(component.chart()?.previousClose!.y).toBeGreaterThan(12);
    expect(component.chart()?.previousClose!.y).toBeLessThan(168);
    updates.next({
      ...overview,
      snapshots: [{ ...snapshot('AAPL', 110, null), previousClose: null }],
    });
    await settle(fixture);
    expect(component.chart()?.previousClose).toBeNull();
    expect(page.querySelector('.previous-close')).toBeNull();
    expect(page.querySelector('.close-legend')).toBeNull();
  });
  it('updates live price and chart endpoint without reloading bars on each snapshot', async () => {
    const updates = new Subject<MarketOverview>();
    service.marketOverview.mockReturnValue(updates);
    const { fixture, component, page } = await render('AAPL');
    updates.next(overview);
    await settle(fixture);
    const calls = service.marketBars.mock.calls.length;
    updates.next({
      ...overview,
      snapshots: [{ ...snapshot('AAPL', 115, 15), timestamp: '2026-10-14T15:02:05Z' }],
    });
    await settle(fixture);
    expect(page.querySelector('.close-price')?.textContent).toContain('$115.00');
    expect(component.chart()?.points.at(-1)?.value).toBe(115);
    expect(service.marketBars).toHaveBeenCalledTimes(calls);
  });
  it('shows an explicit connection state without pretending stored seed prices are live', async () => {
    service.marketOverview.mockReturnValue(
      of({
        ...overview,
        configured: false,
        message: 'Alpaca market data is not connected yet.',
        updatedAt: null,
        snapshots: [],
      }),
    );
    const { page } = await render('AAPL');
    expect(page.textContent).toContain('not connected yet');
    expect(page.querySelector('.close-price')).toBeNull();
    expect(page.querySelector('svg')).toBeNull();
    expect(service.marketBars).not.toHaveBeenCalled();
  });
  it('explains instruments outside Alpaca stock coverage', async () => {
    const { page } = await render('UST10');
    expect(page.querySelector('.instrument-detail')?.textContent).toContain(
      'unavailable for this instrument',
    );
    expect(service.marketBars).not.toHaveBeenCalled();
  });
  it('lets a market leader select an instrument', async () => {
    const { component, page } = await render();
    const choose = vi.fn();
    component.choose.subscribe(choose);
    page.querySelector<HTMLButtonElement>('.leaders button')!.click();
    expect(choose).toHaveBeenCalledWith('MSFT');
  });
  it('keeps the last good prices visible with an error when refresh fails', async () => {
    const { fixture, component, page } = await render();
    service.marketOverview.mockReturnValue(throwError(() => ({ status: 503 })));
    component.refresh();
    await settle(fixture);
    expect(page.querySelector('.load-error')?.textContent).toContain('unavailable');
    expect(page.querySelector('.leaders')?.textContent).toContain('MSFT');
    expect(component.overview()?.updatedAt).toBe(overview.updatedAt);
  });
  it('shows chart failures and retries instead of drawing a fabricated trend', async () => {
    service.marketBars
      .mockReturnValueOnce(
        throwError(() => ({ error: { message: 'Alpaca request limit reached.' } })),
      )
      .mockReturnValue(
        of({ symbol: 'AAPL', feed: 'iex', timeframe: '1Min', message: '', bars: [] }),
      );
    const { fixture, page } = await render('AAPL');
    expect(page.querySelector('.instrument-detail .load-error')?.textContent).toContain(
      'request limit',
    );
    expect(page.querySelector('.price-line')).toBeNull();
    page.querySelector<HTMLButtonElement>('.instrument-detail .load-error button')!.click();
    await settle(fixture);
    expect(page.querySelector('.instrument-detail .load-error')).toBeNull();
  });
  it('cancels an obsolete chart request after selecting another symbol', async () => {
    const earlier = new Subject<MarketChart>();
    service.marketBars.mockReturnValueOnce(earlier).mockReturnValue(
      of({
        symbol: 'MSFT',
        feed: 'iex',
        timeframe: '1Min',
        message: '',
        bars: [bar('2026-10-14T15:01:00Z', 219)],
      }),
    );
    const { fixture, component, page } = await render('AAPL');
    fixture.componentRef.setInput('selectedSymbol', 'MSFT');
    await settle(fixture);
    earlier.next({
      symbol: 'AAPL',
      feed: 'iex',
      timeframe: '1Min',
      message: '',
      bars: [bar('2026-10-14T15:01:00Z', 999)],
    });
    await settle(fixture);
    expect(component.bars()[0].close).toBe(219);
    expect(page.querySelector('#market-instrument-title')?.textContent).toContain('MSFT');
  });
});
