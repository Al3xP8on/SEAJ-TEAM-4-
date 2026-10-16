import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { MarketChart, MarketSnapshot } from '../../orders/orders.models';
import { DashboardService } from '../dashboard.service';
import { StockPosition, StockSummaryComponent } from './stock-summary';

const HISTORY: MarketChart = {
  symbol: 'AAPL',
  feed: 'iex',
  timeframe: '1Day',
  // Out of order on purpose
  bars: [
    { timestamp: '2026-10-14T04:00:00Z', open: 105, high: 112, low: 101, close: 110, volume: 1 },
    { timestamp: '2026-10-13T04:00:00Z', open: 100, high: 106, low: 97, close: 104, volume: 1 },
  ],
  message: '',
};

const SNAPSHOT: MarketSnapshot = {
  symbol: 'AAPL',
  price: 111,
  timestamp: '2026-10-15T15:00:00Z',
  previousClose: 110,
  changePercent: 0.9091,
  dailyBar: {
    timestamp: '2026-10-15T04:00:00Z',
    open: 110,
    high: 112.5,
    low: 109.25,
    close: 111,
    volume: 1,
  },
  quote: null,
};

describe('StockSummaryComponent', () => {
  const dashboard = { bars: vi.fn(() => of(HISTORY)) };

  async function render(snapshot: MarketSnapshot | null, position: StockPosition | null) {
    TestBed.configureTestingModule({
      imports: [StockSummaryComponent],
      providers: [{ provide: DashboardService, useValue: dashboard }],
    });
    const fixture = TestBed.createComponent(StockSummaryComponent);
    fixture.componentRef.setInput('symbol', 'AAPL');
    fixture.componentRef.setInput('snapshot', snapshot);
    fixture.componentRef.setInput('position', position);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    const stats = Object.fromEntries(
      Array.from(page.querySelectorAll('dl > div')).map((d) => [
        d.querySelector('dt')?.textContent?.trim(),
        d.querySelector('dd')?.textContent?.replace(/\s+/g, ' ').trim(),
      ]),
    );
    return { page, stats };
  }

  it('shows the price, today, both ranges and the position', async () => {
    const { stats } = await render(SNAPSHOT, {
      quantity: 10,
      averageCost: 100,
      value: 1110,
      pnl: 110,
    });

    expect(stats).toEqual({
      Price: '$111.00',
      Today: '▲ +$1.00 (+0.91%)',
      "Today's range": '$109.25 – $112.50',
      '30-day range': '$97.00 – $112.00',
      Shares: '10',
      'Average cost': '$100.00',
      Value: '$1,110.00',
      'P&L': '+$110.00',
    });
  });

  it('charts closing prices oldest first, described for screen readers', async () => {
    const { page } = await render(SNAPSHOT, null);

    expect(dashboard.bars).toHaveBeenCalledWith('AAPL', '1Day');
    expect(page.querySelector('svg')?.getAttribute('aria-label')).toBe(
      'AAPL closing price over the last 2 trading days, from $104.00 to $110.00. Use the arrow keys to read values.',
    );
  });

  it("says when you don't hold the stock, and copes without live prices", async () => {
    const { page, stats } = await render(null, null);

    expect(page.querySelector('.not-held')?.textContent?.trim()).toBe("You don't hold AAPL.");
    // Falls back to the last close; today's figures are unavailable rather than zero
    expect(stats['Price']).toBe('$110.00');
    expect(stats['Today']).toBe('—Not available');
  });
});
