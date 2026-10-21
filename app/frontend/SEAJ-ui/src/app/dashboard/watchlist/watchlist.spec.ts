import { TestBed } from '@angular/core/testing';
import { MarketSnapshot } from '../../orders/orders.models';
import { WatchlistComponent } from './watchlist';

const snap = (symbol: string, price: number, changePercent: number | null): MarketSnapshot => ({
  symbol,
  price,
  timestamp: '2026-10-15T19:59:00Z',
  previousClose: price,
  changePercent,
  dailyBar: null,
  quote: null,
});

describe('WatchlistComponent', () => {
  async function render() {
    localStorage.clear();
    TestBed.configureTestingModule({ imports: [WatchlistComponent] });
    const fixture = TestBed.createComponent(WatchlistComponent);
    fixture.componentRef.setInput('accountId', 'ACC-1');
    fixture.componentRef.setInput('snapshots', [snap('AAPL', 190, 1.5), snap('TSLA', 240, -2.25)]);
    fixture.componentRef.setInput('names', new Map([['AAPL', 'Apple Inc.']]));
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    const input = page.querySelector('input') as HTMLInputElement;
    const submit = async (value: string) => {
      input.value = value;
      input.dispatchEvent(new Event('input'));
      page.querySelector('form')!.dispatchEvent(new Event('submit'));
      await fixture.whenStable();
    };
    const rows = () =>
      Array.from(page.querySelectorAll('.rows li')).map((li) =>
        li.textContent?.replace(/\s+/g, ' ').trim(),
      );
    return { fixture, page, input, submit, rows };
  }

  it('starts empty with a hint and a labelled field', async () => {
    const { page, input } = await render();

    expect(page.textContent).toContain('Add tickers you want to keep an eye on.');
    expect(page.querySelector(`label[for="${input.id}"]`)?.textContent).toBe('Add a ticker');
  });

  it('adds a ticker with its name, live price and daily change', async () => {
    const { submit, rows, input } = await render();

    await submit('aapl');

    expect(rows()).toEqual(['AAPL Apple Inc. $190.00 +1.50% Remove AAPL from watchlist']);
    expect(input.value).toBe('');
  });

  it('explains why a ticker cannot be added', async () => {
    const { submit, page, input } = await render();
    const error = () => page.querySelector('#watchlist-error')?.textContent;

    await submit('');
    expect(error()).toBe('Enter a ticker symbol, such as AAPL.');

    await submit('ZZZZ');
    expect(error()).toBe("ZZZZ isn't a tradable instrument.");
    expect(input.getAttribute('aria-invalid')).toBe('true');
    expect(input.getAttribute('aria-describedby')).toBe('watchlist-error');

    await submit('TSLA');
    await submit('tsla');
    expect(error()).toBe('TSLA is already on your watchlist.');
  });

  it('removes a ticker', async () => {
    const { fixture, page, submit, rows } = await render();
    await submit('TSLA');

    (
      Array.from(page.querySelectorAll('.rows button')).find((b) =>
        b.textContent?.includes('Remove'),
      ) as HTMLButtonElement
    ).click();
    await fixture.whenStable();

    expect(rows()).toEqual([]);
  });
});
