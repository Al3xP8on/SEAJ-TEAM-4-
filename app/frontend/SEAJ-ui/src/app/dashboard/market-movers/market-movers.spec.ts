import { TestBed } from '@angular/core/testing';
import { MarketSnapshot } from '../../orders/orders.models';
import { MarketMoversComponent, marketMovers } from './market-movers';

const snap = (
  symbol: string,
  changePercent: number | null,
  timestamp = '2026-10-15T19:59:00Z',
): MarketSnapshot => ({
  symbol,
  price: 100,
  timestamp,
  previousClose: 100,
  changePercent,
  dailyBar: null,
  quote: null,
});

const SNAPSHOTS = [
  snap('AAPL', 3.4),
  snap('MSFT', -1.2),
  snap('NVDA', 5.1),
  snap('TSLA', -4.8),
  snap('AMZN', 0.6),
  snap('META', 1.9),
  snap('JPM', -0.3),
  snap('XOM', null),
  // Last traded the session before, so its move is stale
  snap('OLD', 25, '2026-10-14T19:59:00Z'),
];

describe('marketMovers', () => {
  it('ranks the top three rises and falls from the latest session', () => {
    const { gainers, losers } = marketMovers(SNAPSHOTS);

    expect(gainers.map((m) => [m.symbol, m.change])).toEqual([
      ['NVDA', 0.051],
      ['AAPL', 0.034],
      ['META', 0.019],
    ]);
    expect(losers.map((m) => m.symbol)).toEqual(['TSLA', 'MSFT', 'JPM']);
  });

  it('has nothing to rank without market data', () => {
    expect(marketMovers([])).toEqual({ gainers: [], losers: [] });
  });
});

describe('MarketMoversComponent', () => {
  async function render(snapshots: MarketSnapshot[]) {
    TestBed.configureTestingModule({ imports: [MarketMoversComponent] });
    const fixture = TestBed.createComponent(MarketMoversComponent);
    fixture.componentRef.setInput('snapshots', snapshots);
    fixture.componentRef.setInput('names', new Map([['NVDA', 'NVIDIA Corporation']]));
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  const rows = (page: HTMLElement, list: string) =>
    Array.from(page.querySelectorAll(`[aria-labelledby="movers-${list}"] li`)).map((li) =>
      li.textContent?.replace(/\s+/g, ' ').trim(),
    );

  it('lists gainers and losers with names, prices and signed changes', async () => {
    const page = await render(SNAPSHOTS);

    expect(rows(page, 'gainers')[0]).toBe('NVDA NVIDIA Corporation $100.00 ▲ +5.10%');
    expect(rows(page, 'losers')[0]).toBe('TSLA $100.00 ▼ -4.80%');
  });

  it('says when one side has no movers', async () => {
    const page = await render([snap('AAPL', 2)]);

    expect(page.querySelector('.columns')?.textContent).toContain('Nothing is down today.');
  });

  it('explains when market data is unavailable', async () => {
    const page = await render([]);

    expect(page.querySelector('.empty')?.textContent).toContain("isn't available");
  });
});
