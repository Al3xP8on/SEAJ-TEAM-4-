import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { MarketBar, MarketChart } from '../../orders/orders.models';
import { DashboardService } from '../dashboard.service';
import { CandlestickChartComponent, toCandles } from './candlestick-chart';

const day = (date: string, open: number, high: number, low: number, close: number): MarketBar => ({
  timestamp: `${date}T04:00:00Z`,
  open,
  high,
  low,
  close,
  volume: 1000,
});

// Out of order on purpose; one up day, one down day
const BARS = [day('2026-10-14', 105, 106, 98, 99), day('2026-10-13', 100, 108, 99, 105)];

describe('toCandles', () => {
  it('sorts oldest first and marks direction and change', () => {
    const candles = toCandles(BARS);

    expect(candles.map((c) => [c.timestamp.slice(0, 10), c.up])).toEqual([
      ['2026-10-13', true],
      ['2026-10-14', false],
    ]);
    expect(candles[0].change).toBeCloseTo(0.05);
  });
});

describe('CandlestickChartComponent', () => {
  const chart: MarketChart = {
    symbol: 'AAPL',
    feed: 'iex',
    timeframe: '1Day',
    bars: BARS,
    message: '',
  };
  const dashboard = { bars: vi.fn(() => of(chart)) };

  async function render() {
    TestBed.configureTestingModule({
      imports: [CandlestickChartComponent],
      providers: [{ provide: DashboardService, useValue: dashboard }],
    });
    const fixture = TestBed.createComponent(CandlestickChartComponent);
    fixture.componentRef.setInput('symbol', 'AAPL');
    fixture.componentRef.setInput('name', 'Apple Inc.');
    document.body.appendChild(fixture.nativeElement);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    const text = (selector: string) =>
      page.querySelector(selector)?.textContent?.replace(/\s+/g, ' ').trim();
    return { fixture, page, text };
  }

  beforeEach(() => dashboard.bars.mockClear());

  it('draws hollow up candles and filled down candles from daily bars', async () => {
    const { page } = await render();

    expect(dashboard.bars).toHaveBeenCalledWith('AAPL', '1Day');
    const candles = Array.from(page.querySelectorAll('g.candle'));
    expect(candles.map((c) => c.classList.contains('up'))).toEqual([true, false]);
  });

  it('moves focus to its heading when opened', async () => {
    const { page } = await render();

    expect(document.activeElement).toBe(page.querySelector('#candle-title'));
  });

  it('describes the period and reads each day with the keyboard', async () => {
    const { fixture, page, text } = await render();
    const svg = page.querySelector('svg')!;

    expect(svg.getAttribute('aria-label')).toBe(
      'AAPL daily candles from Oct 13 to Oct 14: opened at $100.00, latest close $99.00, high $108.00, low $98.00. Use the arrow keys to read each day.',
    );
    expect(text('.readout')).toBe('Down 1.00% over the period · High $108.00 · Low $98.00');

    svg.dispatchEvent(new KeyboardEvent('keydown', { key: 'Home' }));
    await fixture.whenStable();
    expect(text('.live')).toBe(
      'Tue Oct 13: open $100.00, high $108.00, low $99.00, close $105.00, up 5.00%',
    );
  });

  it('offers the same data as a table', async () => {
    const { page } = await render();
    const rows = Array.from(page.querySelectorAll('details table tbody tr')).map((r) =>
      Array.from(r.children)
        .map((cell) => cell.textContent?.trim())
        .join(' '),
    );

    expect(rows).toEqual([
      'Oct 13 $100.00 $108.00 $99.00 $105.00 +5.00%',
      'Oct 14 $105.00 $106.00 $98.00 $99.00 -5.71%',
    ]);
  });

  it('asks to close', async () => {
    const { fixture, page } = await render();
    let closed = false;
    fixture.componentInstance.closed.subscribe(() => (closed = true));

    (page.querySelector('.close') as HTMLButtonElement).click();

    expect(closed).toBe(true);
  });
});
