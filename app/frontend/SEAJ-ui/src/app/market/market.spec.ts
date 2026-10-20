import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { EMPTY, of, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { MarketComponent } from './market';
import { OrdersService } from '../orders/orders.service';
import { MarketSnapshot } from '../orders/orders.models';

describe('Market page', () => {
  beforeEach(() =>
    TestBed.configureTestingModule({
      imports: [MarketComponent],
      providers: [
        provideRouter([]),
        {
          provide: AuthService,
          useValue: { getToken: () => 'x.' + btoa(JSON.stringify({ sub: '1' })) + '.x' },
        },
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: OrdersService,
          useValue: {
            marketOverview: () => EMPTY,
            marketBars: vi.fn(() => of({ bars: [], message: '' })),
          },
        },
      ],
    }),
  );
  function setup() {
    const fixture = TestBed.createComponent(MarketComponent);
    TestBed.inject(HttpTestingController)
      .expectOne('/api/v1/instruments')
      .flush([
        { symbol: 'AAA', name: 'Alpha', assetClass: 'Equity', currency: 'USD', tradable: true },
        { symbol: 'BBB', name: 'Bond', assetClass: 'Bond', currency: 'USD', tradable: false },
        { symbol: 'CCC', name: 'No quote', assetClass: 'Equity', currency: 'USD', tradable: true },
      ]);
    const component = fixture.componentInstance;
    component.overview.set({
      configured: true,
      feed: 'test',
      updatedAt: null,
      message: '',
      unsupportedSymbols: [],
      snapshots: [
        {
          symbol: 'AAA',
          price: 105,
          changePercent: 5,
          timestamp: '2026-10-19T16:00:00Z',
          dailyBar: { volume: 200 },
        },
        {
          symbol: 'BBB',
          price: 98,
          changePercent: -2,
          timestamp: '2026-10-19T16:00:00Z',
          dailyBar: { volume: 500 },
        },
      ] as MarketSnapshot[],
    });
    return { fixture, component };
  }
  it('defaults to every instrument including those without quotes and nontradable instruments', () => {
    const { fixture, component } = setup();
    fixture.detectChanges();
    expect(component.assetClass()).toBe('');
    expect(component.rows()).toHaveLength(3);
    expect(component.analytics().quoted).toBe(2);
    expect(fixture.nativeElement.textContent).toContain('No quote');
  });
  it('applies asset selection to analytics, tables and rankings', () => {
    const { component } = setup();
    component.assetClass.set('Bond');
    expect(component.rows().map((r) => r.symbol)).toEqual(['BBB']);
    expect(component.analytics().declining).toBe(1);
    expect(component.leaders()).toEqual([]);
    component.ranking.set('losers');
    expect(component.leaders().map((r) => r.symbol)).toEqual(['BBB']);
  });
  it('sorts active instruments and excludes missing historical metrics', () => {
    const { component } = setup();
    component.ranking.set('active');
    expect(component.leaders().map((r) => r.symbol)).toEqual(['BBB', 'AAA']);
    component.ranking.set('performance');
    expect(component.leaders()).toEqual([]);
    component.ranking.set('trending');
    expect(component.leaders()).toEqual([]);
  });
  it('computes completed-session returns and relative volume without including the current session', () => {
    const { component } = setup();
    component.histories.set({
      AAA: Array.from({ length: 22 }, (_, i) => ({
        symbol: 'AAA',
        priceDate: new Date(Date.UTC(2026, 8, 28 + i)).toISOString().slice(0, 10),
        close: 100 + i,
        open: 100,
        high: 130,
        low: 90,
        volume: i === 21 ? 99999 : 100,
      })),
    });
    component.ranking.set('performance');
    expect(component.leaders()[0].value).toBeCloseTo(20);
    component.ranking.set('trending');
    expect(component.leaders()[0].value).toBe(2);
  });
  it('searches both symbols and instrument names', () => {
    const { component } = setup();
    component.search.set('alpha');
    expect(component.visibleRows().map((r) => r.symbol)).toEqual(['AAA']);
  });
  it('removes summary cards and updates headings with the selected asset class', () => {
    const { fixture, component } = setup();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.analytics')).toBeNull();
    expect(fixture.nativeElement.querySelector('.filters button').textContent.trim()).toBe(
      'Overview',
    );
    component.assetClass.set('Bond');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('h1').textContent).toBe('Bond overview');
    expect(fixture.nativeElement.querySelector('#bond-title').textContent).toBe('Individual bonds');
    TestBed.inject(HttpTestingController)
      .expectOne('/api/v1/market-data/bonds')
      .flush({
        configured: true,
        environment: 'Sandbox',
        checkedAt: null,
        bonds: [],
        message: 'Treasury access is not enabled for this Alpaca subscription.',
      });
  });
  it('shows up and down arrows only for instruments with a daily price change', () => {
    const { fixture } = setup();
    fixture.detectChanges();
    const rows = fixture.nativeElement.querySelectorAll('tbody tr');
    expect(rows[0].querySelector('.direction').textContent.trim()).toBe('↑');
    expect(rows[1].querySelector('.direction').textContent.trim()).toBe('↓');
    expect(rows[2].querySelector('.direction')).toBeNull();
  });
  it('opens a visible chart dialog from the table button and allows closing it', () => {
    const { fixture, component } = setup();
    fixture.detectChanges();
    const dialog = fixture.nativeElement.querySelector('dialog') as HTMLDialogElement;
    const show = vi.fn(() => dialog.setAttribute('open', ''));
    const close = vi.fn(() => dialog.removeAttribute('open'));
    Object.defineProperties(dialog, { showModal: { value: show }, close: { value: close } });
    fixture.nativeElement.querySelector('tbody .text-button').click();
    fixture.detectChanges();
    expect(show).toHaveBeenCalledOnce();
    expect(dialog.open).toBe(true);
    expect(dialog.textContent).toContain('AAA · Market detail');
    expect(dialog.querySelector('a')?.getAttribute('href')).toBe('/order?symbol=AAA');
    vi.spyOn(dialog, 'getBoundingClientRect').mockReturnValue({
      left: 100,
      right: 900,
      top: 100,
      bottom: 700,
    } as DOMRect);
    dialog.dispatchEvent(new MouseEvent('click', { bubbles: true, clientX: 150, clientY: 150 }));
    expect(close).not.toHaveBeenCalled();
    dialog.querySelector('h2')!.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    expect(close).not.toHaveBeenCalled();
    dialog.dispatchEvent(new MouseEvent('click', { bubbles: true, clientX: 50, clientY: 150 }));
    expect(close).toHaveBeenCalledOnce();
    expect(component.selected()).toBe('');
  });
  it('populates historical rankings from market-feed daily bars without stored price history', async () => {
    const bars = Array.from({ length: 22 }, (_, i) => ({
      timestamp: new Date(Date.UTC(2026, 8, 28 + i, 4)).toISOString(),
      close: 100 + i,
      open: 100,
      high: 130,
      low: 90,
      volume: i === 21 ? 99999 : 100,
    }));
    const service = TestBed.inject(OrdersService);
    vi.mocked(service.marketBars).mockReturnValue(
      of({ symbol: 'AAA', timeframe: '1Day', feed: 'test', message: '', bars }),
    );
    const { fixture, component } = setup();
    fixture.detectChanges();
    await fixture.whenStable();
    component.ranking.set('performance');
    expect(service.marketBars).toHaveBeenCalledWith('AAA', '1Day');
    expect(component.leaders()[0].value).toBeCloseTo(20);
    component.ranking.set('trending');
    expect(component.leaders().find((r) => r.symbol === 'AAA')!.value).toBe(2);
    expect(component.historyLoading()).toBe(false);
  });
  it('reports historical feed failures and retries without silently treating them as no data', async () => {
    const service = TestBed.inject(OrdersService);
    vi.mocked(service.marketBars).mockReturnValue(throwError(() => new Error('Unavailable')));
    const { fixture, component } = setup();
    component.ranking.set('trending');
    fixture.detectChanges();
    await fixture.whenStable();
    expect(component.historyFailures()).toBe(2);
    expect(fixture.nativeElement.textContent).toContain('Historical data could not be loaded');
    vi.mocked(service.marketBars).mockReturnValue(
      of({ symbol: 'AAA', timeframe: '1Day', feed: 'test', message: '', bars: [] }),
    );
    component.retryHistory();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(component.historyFailures()).toBe(0);
  });

  it('shows forex pairs with correct inversions, daily changes and charts', async () => {
    const { fixture, component } = setup();
    component.assetClass.set('Forex');
    fixture.detectChanges();
    TestBed.inject(HttpTestingController)
      .expectOne(component.fxUrl)
      .flush({
        base: 'USD',
        message: '',
        sessions: [
          { date: '2026-10-15', rates: { EUR: 0.8, GBP: 0.75, JPY: 150 } },
          { date: '2026-10-16', rates: { EUR: 0.9, GBP: 0.8, JPY: 153 } },
        ],
      });
    await fixture.whenStable();
    fixture.detectChanges();
    const rows = component.forexRows();
    expect(rows.find((r) => r.symbol === 'EUR/USD')!.latest!.value).toBeCloseTo(1 / 0.9);
    expect(rows.find((r) => r.symbol === 'USD/JPY')!.change).toBeCloseTo(2);
    expect(rows.find((r) => r.symbol === 'USD/CHF')!.latest).toBeUndefined();
    expect(fixture.nativeElement.textContent).toContain('not live bid/ask');
    expect(fixture.nativeElement.querySelector('.forex-chart svg')).toBeNull();
    const dialog = fixture.nativeElement.querySelector('dialog') as HTMLDialogElement;
    const close = vi.fn(() => dialog.removeAttribute('open'));
    Object.defineProperties(dialog, {
      showModal: { value: () => dialog.setAttribute('open', '') },
      close: { value: close },
    });
    const chartButtons = fixture.nativeElement.querySelectorAll('tbody .text-button');
    chartButtons[2].click();
    fixture.detectChanges();
    expect(component.forexChart()!.symbol).toBe('USD/JPY');
    expect(dialog.open).toBe(true);
    expect(dialog.querySelector('.forex-chart svg')).toBeTruthy();
    expect(dialog.textContent).toContain('USD/JPY · Forex analytics');
    vi.spyOn(dialog, 'getBoundingClientRect').mockReturnValue({
      left: 100,
      right: 900,
      top: 100,
      bottom: 700,
    } as DOMRect);
    dialog.querySelector('h2')!.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    expect(close).not.toHaveBeenCalled();
    dialog.dispatchEvent(new MouseEvent('click', { bubbles: true, clientX: 50, clientY: 150 }));
    expect(close).toHaveBeenCalledOnce();
    expect(dialog.open).toBe(false);
    expect(fixture.nativeElement.querySelector('#cash-title')).toBeNull();
  });
  it('shows an unavailable state when the forex feed fails', async () => {
    const { fixture, component } = setup();
    component.assetClass.set('Forex');
    fixture.detectChanges();
    TestBed.inject(HttpTestingController)
      .expectOne(component.fxUrl)
      .flush({}, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(component.forexChart()).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Forex rates could not be refreshed');
    expect(fixture.nativeElement.textContent).toContain('Unavailable');
  });
  it('renders genuine Broker bonds, filters by type, and opens read-only details', async () => {
    const { fixture, component } = setup();
    component.assetClass.set('Bond');
    fixture.detectChanges();
    TestBed.inject(HttpTestingController)
      .expectOne('/api/v1/market-data/bonds')
      .flush({
        configured: true,
        environment: 'Sandbox',
        checkedAt: '2026-10-20T12:00:00Z',
        message: '',
        bonds: [
          {
            isin: 'US912797MU86',
            cusip: '912797MU8',
            type: 'Treasury',
            name: 'Treasury Bill',
            issuer: 'U.S. Treasury',
            maturityDate: '2027-03-27',
            coupon: 0,
            closePrice: 99.6,
            priceDate: '2026-10-19',
            yieldToMaturity: 4.2,
            tradable: true,
          },
          {
            isin: 'US00138CAU27',
            cusip: null,
            type: 'Corporate bond',
            name: 'Corporate Bond',
            issuer: 'Issuer',
            maturityDate: null,
            coupon: 5,
            closePrice: null,
            priceDate: null,
            yieldToMaturity: null,
            tradable: false,
          },
        ],
      });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('99.6');
    expect(fixture.nativeElement.textContent).toContain('2026-10-19');
    expect(fixture.nativeElement.textContent).toContain('Alpaca Broker · fixed income');
    component.bondType.set('Treasury');
    expect(component.visibleBonds()).toHaveLength(1);
    component.bondSearch.set('912797');
    expect(component.visibleBonds()).toHaveLength(1);
    const dialog = fixture.nativeElement.querySelector('dialog') as HTMLDialogElement;
    Object.defineProperty(dialog, 'showModal', { value: () => dialog.setAttribute('open', '') });
    component.viewBond(component.visibleBonds()[0]);
    fixture.detectChanges();
    expect(dialog.open).toBe(true);
    expect(dialog.textContent).toContain('Treasury Bill');
    expect(dialog.querySelector('a')).toBeNull();
  });
});
