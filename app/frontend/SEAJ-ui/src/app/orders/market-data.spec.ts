import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { DailyPrice, Instrument } from './orders.models';
import { MarketDataComponent } from './market-data';
import { OrdersService } from './orders.service';

const instruments: Instrument[] = [
  { symbol: 'AAPL', name: 'Apple', assetClass: 'EQUITY', currency: 'USD', tradable: true },
  { symbol: 'MSFT', name: 'Microsoft', assetClass: 'EQUITY', currency: 'USD', tradable: true },
  {
    symbol: 'GLBEQ1',
    name: 'Global Equity Fund',
    assetClass: 'FUND',
    currency: 'GBP',
    tradable: true,
  },
];
const daily = (symbol: string, priceDate: string, close: number): DailyPrice => ({
  symbol,
  priceDate,
  close,
  open: close + 1,
  high: close + 2,
  low: close - 2,
  volume: 10000,
});

describe('MarketDataComponent', () => {
  const service = { priceHistory: vi.fn() };
  beforeEach(async () => {
    service.priceHistory.mockReset().mockReturnValue(of([]));
    await TestBed.configureTestingModule({
      imports: [MarketDataComponent],
      providers: [{ provide: OrdersService, useValue: service }],
    }).compileComponents();
  });
  async function render(symbol = '') {
    const fixture = TestBed.createComponent(MarketDataComponent);
    fixture.componentRef.setInput('instruments', instruments);
    fixture.componentRef.setInput('selectedSymbol', symbol);
    fixture.detectChanges();
    await new Promise((resolve) => setTimeout(resolve, 0));
    await fixture.whenStable();
    return {
      fixture,
      component: fixture.componentInstance,
      page: fixture.nativeElement as HTMLElement,
    };
  }

  it('ranks close-to-close percentage gains in descending order, independent of response order and currency', async () => {
    service.priceHistory.mockImplementation((symbol: string) =>
      of([
        daily(symbol, '2026-10-14', symbol === 'AAPL' ? 110 : symbol === 'MSFT' ? 240 : 95),
        daily(symbol, '2026-10-13', symbol === 'MSFT' ? 200 : 100),
      ]),
    );
    const { component, page } = await render();
    expect(component.leaders().map((entry) => entry.instrument.symbol)).toEqual([
      'MSFT',
      'AAPL',
      'GLBEQ1',
    ]);
    expect(component.leaders()[0].gain).toBeCloseTo(20);
    expect(page.querySelector('.leaders')?.textContent).toContain('+20.00%');
    expect(page.querySelector('.negative')?.textContent).toContain('-5.00%');
    expect(page.querySelector('.leaders')?.textContent).toContain('£95.00');
    expect(page.querySelector('.data-date')?.textContent).toContain('14 Oct 2026');
  });
  it('excludes instruments whose latest close is older than the ranking date', async () => {
    service.priceHistory.mockImplementation((symbol: string) =>
      of(
        symbol === 'AAPL'
          ? [daily(symbol, '2026-10-13', 100), daily(symbol, '2026-10-14', 105)]
          : [daily(symbol, '2026-10-12', 100), daily(symbol, '2026-10-13', 150)],
      ),
    );
    const { component } = await render();
    expect(component.leaders().map((entry) => entry.instrument.symbol)).toEqual(['AAPL']);
  });
  it('shows a single recorded point without inventing a trend or daily gain', async () => {
    service.priceHistory.mockImplementation((symbol: string) =>
      of(symbol === 'AAPL' ? [daily(symbol, '2026-10-12', 154)] : []),
    );
    const { component, page } = await render('AAPL');
    expect(component.leaders()).toEqual([]);
    expect(component.selectedGain()).toBeNull();
    expect(page.querySelector('.price-line')).toBeNull();
    expect(page.querySelectorAll('.price-point')).toHaveLength(1);
    expect(page.textContent).toContain('More history is needed');
    expect(page.textContent).toContain('Daily rankings need two daily closes');
    expect(page.querySelector('.close-price')?.textContent).toContain('$154.00');
  });
  it('plots recorded closes and updates details when the selected symbol changes', async () => {
    service.priceHistory.mockImplementation((symbol: string) =>
      of([daily(symbol, '2026-10-13', 100), daily(symbol, '2026-10-14', 105)]),
    );
    const { fixture, component, page } = await render('AAPL');
    expect(page.querySelector('.price-line')).not.toBeNull();
    expect(page.querySelectorAll('.price-point')).toHaveLength(2);
    expect(component.selectedGain()).toBeCloseTo(5);
    const loaded = service.priceHistory.mock.calls.length;
    fixture.componentRef.setInput('selectedSymbol', 'GLBEQ1');
    await fixture.whenStable();
    expect(page.querySelector('#market-instrument-title')?.textContent).toContain('GLBEQ1');
    expect(page.querySelector('.close-price')?.textContent).toContain('£105.00');
    expect(service.priceHistory).toHaveBeenCalledTimes(loaded);
  });
  it('lets a market leader select an instrument', async () => {
    service.priceHistory.mockImplementation((symbol: string) =>
      of([daily(symbol, '2026-10-13', 100), daily(symbol, '2026-10-14', 105)]),
    );
    const { component, page } = await render();
    const choose = vi.fn();
    component.choose.subscribe(choose);
    page.querySelector<HTMLButtonElement>('.leaders button')!.click();
    expect(choose).toHaveBeenCalledWith('AAPL');
  });
  it('distinguishes failed histories from empty histories, preserves other instruments, and supports retry', async () => {
    service.priceHistory.mockImplementation((symbol: string) =>
      symbol === 'MSFT'
        ? throwError(() => ({ status: 503 }))
        : of([daily(symbol, '2026-10-13', 100), daily(symbol, '2026-10-14', 105)]),
    );
    const { fixture, component, page } = await render('MSFT');
    expect(component.leaders()).toHaveLength(2);
    expect(page.querySelector('.instrument-detail')?.textContent).toContain('could not be loaded');
    expect(page.querySelector('.load-error')?.textContent).toContain('1 instrument histories');
    service.priceHistory.mockReturnValue(of([]));
    page.querySelector<HTMLButtonElement>('.load-error button')!.click();
    fixture.detectChanges();
    await new Promise((resolve) => setTimeout(resolve, 0));
    await fixture.whenStable();
    expect(component.failedSymbols()).toEqual([]);
    expect(page.querySelector('.instrument-detail')?.textContent).toContain(
      'No daily price history',
    );
  });
  it('keeps simulated quotes separate from stored history and ignores quotes for another symbol', async () => {
    const { fixture, page } = await render('AAPL');
    fixture.componentRef.setInput('quote', {
      symbol: 'AAPL',
      lastPrice: 100.52,
      bidPrice: 100.5,
      askPrice: 100.55,
      priceTimeStamp: '2026-10-14T10:00:00Z',
    });
    await fixture.whenStable();
    expect(page.querySelector('.quote-note')?.textContent).toContain('Simulated trading quote');
    expect(page.querySelector('.quote-note')?.textContent).toContain('$100.52');
    expect(page.querySelector('svg')).toBeNull();
    fixture.componentRef.setInput('selectedSymbol', 'MSFT');
    await fixture.whenStable();
    expect(page.querySelector('.quote-note')).toBeNull();
  });
});
