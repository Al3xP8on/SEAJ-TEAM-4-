import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Observable, Subject, of, throwError } from 'rxjs';
import { DashboardComponent } from './dashboard';
import { DashboardData, DashboardService } from './dashboard.service';
import { Position } from './dashboard.models';
import { MarketOverview, MarketSnapshot, Order } from '../orders/orders.models';

const order = (
  orderId: string,
  ticker: string,
  createdAt: string,
  extra: Partial<Order> = {},
): Order => ({
  orderId,
  accountId: 'ACC-0001',
  accountHolderName: 'Alice Johnson',
  ticker,
  instrumentName: ticker,
  side: 'BUY',
  quantity: 10,
  price: 100,
  totalValue: 1000,
  status: 'FILLED',
  createdAt,
  orderType: 'LIMIT',
  executionPrice: null,
  ...extra,
});

const DATA: DashboardData = {
  account: {
    id: 1,
    accountId: 'ACC-0001',
    holderName: 'Alice Johnson',
    cashBalance: 10000,
    status: 'ACTIVE',
  },
  positions: [
    { positionId: 1, symbol: 'AAPL', quantity: 50, averageCost: 182.4, status: 'OPEN' },
    { positionId: 2, symbol: 'MSFT', quantity: 20, averageCost: 415.1, status: 'OPEN' },
    {
      positionId: 3,
      symbol: 'NVDA',
      quantity: 15,
      averageCost: 118.75,
      status: 'PARTIALLY_CLOSED',
    },
    { positionId: 4, symbol: 'TSLA', quantity: 0, averageCost: 240, status: 'CLOSED' },
  ],
  orders: [
    order('o1', 'AAPL', '2026-10-01T09:00:00'),
    order('o2', 'MSFT', '2026-10-06T14:30:00', { side: 'SELL', status: 'PENDING' }),
    order('o3', 'NVDA', '2026-10-02T09:00:00', { executionPrice: 118.75, price: 120 }),
    order('o4', 'TSLA', '2026-10-03T09:00:00', { status: 'REJECTED' }),
    order('o5', 'AMZN', '2026-10-04T09:00:00'),
    order('o6', 'META', '2026-10-05T09:00:00', { status: 'CANCELLED' }),
  ],
};

const snapshot = (symbol: string, price: number, previousClose: number | null): MarketSnapshot => ({
  symbol,
  price,
  timestamp: '2026-10-15T14:30:00Z',
  previousClose,
  changePercent: null,
  dailyBar: null,
  quote: null,
});

// NVDA has no live price, so it stays at cost
const MARKET: MarketOverview = {
  configured: true,
  feed: 'iex',
  updatedAt: '2026-10-15T14:30:05Z',
  message: '',
  snapshots: [snapshot('AAPL', 190, 185), snapshot('MSFT', 400, 410)],
  unsupportedSymbols: [],
};

describe('DashboardComponent', () => {
  const router = { navigate: vi.fn() };
  const dashboard = {
    load: vi.fn<() => Observable<DashboardData>>(),
    marketPrices: vi.fn<() => Observable<MarketOverview | null>>(),
    bars: vi.fn(() => of(null)),
    instrumentNames: vi.fn(() =>
      of(
        new Map([
          ['AAPL', 'Apple Inc.'],
          ['MSFT', 'Microsoft Corporation'],
        ]),
      ),
    ),
  };

  async function render() {
    const fixture = TestBed.createComponent(DashboardComponent);
    await fixture.whenStable();
    return {
      fixture,
      component: fixture.componentInstance,
      page: fixture.nativeElement as HTMLElement,
    };
  }

  const button = (page: HTMLElement, label: string) =>
    Array.from(page.querySelectorAll('button')).find((b) => b.textContent?.trim() === label)!;

  beforeEach(async () => {
    router.navigate.mockReset();
    dashboard.load.mockReset().mockReturnValue(of(DATA));
    dashboard.marketPrices.mockReset().mockReturnValue(of(null));
    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        { provide: Router, useValue: router },
        { provide: DashboardService, useValue: dashboard },
      ],
    }).compileComponents();
  });

  it('labels the account number for screen readers', async () => {
    const { page } = await render();

    expect(page.querySelector('.account-id')?.textContent?.replace(/\s+/g, ' ').trim()).toBe(
      'Account ACC-0001',
    );
  });

  it('welcomes the logged-in account holder by name', async () => {
    const { page } = await render();

    expect(page.querySelector('.welcome')?.textContent).toContain('Welcome, Alice Johnson');
  });

  it('shows the cash balance as currency', async () => {
    const { page } = await render();

    expect(page.querySelector('.balance')?.textContent?.trim()).toBe('$10,000.00');
  });

  it('lists only held positions, with quantity and value', async () => {
    const { page } = await render();
    const rows = Array.from(page.querySelectorAll('.positions-table tbody tr'));

    expect(rows.map((r) => r.querySelector('.symbol')?.textContent)).toEqual([
      'AAPL',
      'MSFT',
      'NVDA',
    ]);
    expect(rows[0].textContent).toContain('50');
    expect(rows[0].textContent).toContain('$9,120.00'); // 50 x 182.40
  });

  it('totals the value of all holdings', async () => {
    const { page } = await render();

    // 9,120.00 + 8,302.00 + 1,781.25
    expect(page.querySelector('.total')?.textContent?.trim()).toBe('$19,203.25');
  });

  it('summarises holdings value and total portfolio (cash + holdings)', async () => {
    const { page } = await render();

    expect(page.querySelector('.holdings-value')?.textContent?.trim()).toBe('$19,203.25');
    expect(page.querySelector('.total-value')?.textContent?.trim()).toBe('$29,203.25'); // 10,000 + 19,203.25
  });

  it('shows allocation across holdings and cash, largest holdings first', async () => {
    const { page } = await render();
    const legend = Array.from(page.querySelectorAll('app-allocation-chart .legend li')).map(
      (li) => [
        li.querySelector('.label')?.textContent?.trim(),
        li.querySelector('.percent')?.textContent?.trim(),
      ],
    );

    // Of the $29,203.25 total: AAPL 9,120, MSFT 8,302, NVDA 1,781.25, cash 10,000
    expect(legend).toEqual([
      ['AAPL', '31.2%'],
      ['MSFT', '28.4%'],
      ['NVDA', '6.1%'],
      ['Cash', '34.2%'],
    ]);
  });

  it('values holdings at cost and hides price-based figures without market data', async () => {
    const { page } = await render();

    expect(page.querySelector('.valuation')?.textContent).toContain('3 holdings, at cost');
    expect(page.querySelector('.day-change')).toBeNull();
    expect(page.querySelector('.as-of')).toBeNull();
    expect(page.querySelector('.total-pnl')?.textContent?.trim()).toBe('');
  });

  it('shows the NYSE status, countdown and New York time', async () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-10-15T14:30:30Z')); // Thursday 10:30 in New York
    dashboard.marketPrices.mockReturnValue(of(MARKET)); // latest trade 14:30:00
    const { page } = await render();

    expect(page.querySelector('.market-status')?.textContent?.replace(/\s+/g, ' ').trim()).toBe(
      'NYSE open · closes in 5 h 30 min',
    );
    expect(page.querySelector('.clock')?.textContent?.trim()).toBe('10:30:30 ET');
    // Prices' time uses the same zone as the clock (MARKET.updatedAt is 14:30:05 UTC)
    expect(page.querySelector('.as-of')?.textContent?.trim()).toBe('10:30:05 ET');
    // The strip sits above the welcome
    const strip = page.querySelector('app-market-clock')!;
    expect(
      strip.compareDocumentPosition(page.querySelector('h1')!) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
    vi.useRealTimers();
  });

  it('values holdings at the latest price, with price and P&L per row', async () => {
    dashboard.marketPrices.mockReturnValue(of(MARKET));
    const { page } = await render();
    const row = (symbol: string) =>
      Array.from(page.querySelectorAll('.positions-table tbody tr')).find(
        (r) => r.querySelector('.symbol')?.textContent === symbol,
      )!;

    expect(row('AAPL').querySelector('.price')?.textContent?.trim()).toBe('$190.00');
    expect(row('AAPL').textContent).toContain('$9,500.00'); // 50 x 190
    expect(row('AAPL').querySelector('.pnl')?.textContent?.trim()).toBe('+$380.00');
    expect(row('MSFT').querySelector('.pnl')?.textContent?.trim()).toBe('-$302.00');
    expect(row('MSFT').querySelector('.pnl')?.classList).toContain('loss');
    // No price for NVDA: valued at cost, with the gap explained to screen readers
    expect(row('NVDA').querySelector('.price')?.textContent).toContain('No live price');
    expect(row('NVDA').textContent).toContain('$1,781.25');

    // 9,500 + 8,000 + 1,781.25, and P&L over priced holdings only: 380 - 302
    expect(page.querySelector('.holdings-value')?.textContent?.trim()).toBe('$19,281.25');
    expect(page.querySelector('.total-pnl')?.textContent?.trim()).toBe('+$78.00');
    expect(page.querySelector('.valuation')?.textContent).toContain('3 holdings, 1 at cost');
  });

  it("shows today's change since the previous close and when prices were updated", async () => {
    dashboard.marketPrices.mockReturnValue(of(MARKET));
    const { page } = await render();

    // AAPL +5 x 50 and MSFT -10 x 20 = +50, on a 17,450 starting value
    expect(page.querySelector('.day-change')?.textContent?.replace(/\s+/g, ' ').trim()).toBe(
      '+$50.00 (+0.29%) today',
    );
    expect(page.querySelector('.as-of')?.getAttribute('datetime')).toBe('2026-10-15T14:30:05Z');
  });

  it('feeds market values into the allocation', async () => {
    dashboard.marketPrices.mockReturnValue(of(MARKET));
    const { page } = await render();
    const percents = Array.from(page.querySelectorAll('app-allocation-chart .legend li')).map(
      (li) => li.querySelector('.percent')?.textContent?.trim(),
    );

    // Of the $29,281.25 total: AAPL 9,500, MSFT 8,000, NVDA 1,781.25 (at cost), cash 10,000
    expect(percents).toEqual(['32.4%', '27.3%', '6.1%', '34.2%']);
  });

  it('explains the numbers in plain language beside the positions', async () => {
    dashboard.marketPrices.mockReturnValue(of(MARKET));
    const { page } = await render();
    // Title and detail are separate paragraphs, so they're read as separate lines
    const rows = Array.from(page.querySelectorAll('app-briefing .item')).map((li) =>
      Array.from(li.querySelectorAll('.title, .detail'))
        .map((p) => p.textContent?.trim())
        .join(' | '),
    );

    // AAPL 9,500 (32%) and MSFT 8,000 (27%) of 29,281.25; MSFT's open SELL holds back no cash
    expect(rows).toEqual([
      'AAPL moved the most | Up $250.00 today',
      'AAPL and MSFT are each above 25% | Above the 25% guideline',
      '1 order waiting to fill',
    ]);
    expect(page.querySelector('app-briefing h2')?.textContent?.trim()).toBe('Insights');
    // It sits in the side column with the allocation, after the summary cards
    expect(page.querySelector('.side-column app-briefing')).not.toBeNull();
    // Icons are decoration; the words carry the meaning
    expect(page.querySelector('app-briefing ng-icon')?.getAttribute('aria-hidden')).toBe('true');
  });

  it('sorts holdings by value and shows company names where known', async () => {
    // At market MSFT (8,000) leads NVDA at cost (1,781.25); make NVDA worth the most
    dashboard.marketPrices.mockReturnValue(
      of({ ...MARKET, snapshots: [...MARKET.snapshots, snapshot('NVDA', 1000, null)] }),
    );
    const { page } = await render();
    const rows = Array.from(page.querySelectorAll('.positions-table tbody tr'));

    // NVDA 15 x 1,000 = 15,000; AAPL 9,500; MSFT 8,000
    expect(rows.map((r) => r.querySelector('.symbol')?.textContent)).toEqual([
      'NVDA',
      'AAPL',
      'MSFT',
    ]);
    expect(rows[1].querySelector('.company')?.textContent).toBe('Apple Inc.');
    // Read as words, not "AAPLApple Inc."
    expect(rows[1].querySelector('th')?.textContent?.replace(/\s+/g, ' ').trim()).toBe(
      'AAPL Apple Inc.',
    );
    expect(rows[0].querySelector('.company')).toBeNull(); // no name known for NVDA
  });

  it("opens a holding's summary in a centred dialog and returns focus on close", async () => {
    dashboard.marketPrices.mockReturnValue(of(MARKET));
    const { fixture, page } = await render();
    document.body.appendChild(page);
    const open = Array.from(page.querySelectorAll('.positions-table .symbol-link')).find(
      (b) => b.textContent?.trim() === 'AAPL',
    ) as HTMLButtonElement;
    expect(open.getAttribute('aria-label')).toBe('View AAPL chart');

    open.focus();
    open.click();
    await fixture.whenStable();
    // The dialog renders in an overlay on the body, outside the dashboard element
    const dialog = document.querySelector('hlm-dialog-content') as HTMLElement;
    expect(dialog.closest('[role="dialog"]')).not.toBeNull();
    expect(dialog.querySelector('h2')?.textContent?.replace(/\s+/g, ' ').trim()).toBe(
      'AAPL Apple Inc.',
    );
    // The position matches the dashboard: 50 shares at 182.40, worth 9,500 at 190
    expect(dialog.querySelector('app-stock-summary .pnl')?.textContent?.trim()).toBe('+$380.00');
    expect(dashboard.bars).toHaveBeenCalledWith('AAPL', '1Day');

    (dialog.querySelector('button[hlmDialogClose]') as HTMLButtonElement).click();
    await new Promise((resolve) => setTimeout(resolve, 300));
    await fixture.whenStable();
    expect(document.querySelector('hlm-dialog-content app-stock-summary')).toBeNull();
    expect(document.activeElement).toBe(open);
    page.remove();
  });

  it('shows a message instead of the table when there are no holdings', async () => {
    const closedOnly: Position[] = [
      { positionId: 9, symbol: 'TSLA', quantity: 0, averageCost: 240, status: 'CLOSED' },
    ];
    dashboard.load.mockReturnValue(of({ ...DATA, positions: closedOnly }));
    const { page } = await render();

    expect(page.querySelector('.positions-table')).toBeNull();
    expect(page.querySelector('.empty')?.textContent).toContain("don't hold any positions");
  });

  it('lists recent orders, newest first', async () => {
    const { page } = await render();
    const rows = Array.from(page.querySelectorAll('.order-list .order'));

    expect(rows.map((r) => r.querySelector('.symbol')?.textContent)).toEqual([
      'MSFT',
      'META',
      'AMZN',
      'TSLA',
      'NVDA',
      'AAPL',
    ]);
    expect(rows[0].textContent).toContain('SELL');
    expect(rows[0].querySelector('.row')?.textContent?.replace(/\s+/g, ' ')).toMatch(
      /SELL 10 MSFT @ \$100\.00 Oct 6, 14:30 PENDING/,
    );
    expect(rows[0].querySelector('[hlmBadge]')?.textContent).toBe('PENDING');
    expect(rows[0].querySelector('[hlmBadge]')?.getAttribute('data-variant')).toBe('secondary');
    expect(rows[0].querySelector('time')?.getAttribute('datetime')).toBe('2026-10-06T14:30:00');
  });

  it('shows the execution price for filled orders, otherwise the order price', async () => {
    const { page } = await render();
    const nvda = Array.from(page.querySelectorAll('.order-list .order')).find(
      (r) => r.querySelector('.symbol')?.textContent === 'NVDA',
    )!;

    expect(nvda.textContent).toContain('$118.75');
    expect(nvda.textContent).not.toContain('$120.00');
  });

  it('shows buying power as cash less open buy orders', async () => {
    // MSFT is an open SELL and does not hold back cash; add an open BUY worth $2,500
    const orders = [
      ...DATA.orders,
      order('o7', 'AMD', '2026-09-01T09:00:00', { status: 'NEW', totalValue: 2500 }),
    ];
    dashboard.load.mockReturnValue(of({ ...DATA, orders }));
    const { page } = await render();

    expect(page.querySelector('.buying-power')?.textContent?.trim()).toBe('$7,500.00');
  });

  it('lists at most the 20 latest orders', async () => {
    const many = Array.from({ length: 25 }, (_, i) =>
      order(`m${i}`, 'AAPL', `2026-09-${String(i + 1).padStart(2, '0')}T09:00:00`),
    );
    dashboard.load.mockReturnValue(of({ ...DATA, orders: many }));
    const { page } = await render();

    expect(page.querySelectorAll('.order-list .order').length).toBe(20);
  });

  it('makes long lists scrollable from the keyboard', async () => {
    const { page } = await render();

    for (const label of ['Positions table', 'Recent orders list']) {
      const region = page.querySelector(`[role="region"][aria-label="${label}"]`);
      expect(region?.getAttribute('tabindex')).toBe('0');
    }
  });

  it('shows a message instead of the order list when there are no orders', async () => {
    dashboard.load.mockReturnValue(of({ ...DATA, orders: [] }));
    const { page } = await render();

    expect(page.querySelector('.order-list')).toBeNull();
    expect(page.querySelector('.orders-card .empty')?.textContent).toContain(
      "haven't placed any orders",
    );
  });

  it('shows a loading message until the data arrives', async () => {
    const pending = new Subject<DashboardData>();
    dashboard.load.mockReturnValue(pending);
    const { fixture, page } = await render();

    expect(page.querySelector('.status')?.textContent).toContain('Loading');
    expect(page.querySelector('.welcome')).toBeNull();

    pending.next(DATA);
    pending.complete();
    await fixture.whenStable();
    expect(page.querySelector('.welcome')?.textContent).toContain('Alice Johnson');
  });

  it('shows an error with a retry that reloads the data', async () => {
    dashboard.load.mockReturnValueOnce(throwError(() => new Error('offline')));
    const { fixture, page } = await render();

    expect(page.querySelector('[role="alert"]')?.textContent).toContain(
      'couldn’t load your account',
    );

    button(page, 'Try again').click();
    await fixture.whenStable();
    expect(dashboard.load).toHaveBeenCalledTimes(2);
    expect(page.querySelector('.welcome')?.textContent).toContain('Alice Johnson');
  });

  it.each([
    ['Place Order', '/order'],
    ['View all orders', '/order'],
  ])('%s button navigates to %s', async (label, path) => {
    const { page } = await render();

    button(page, label).click();

    expect(router.navigate).toHaveBeenCalledWith([path]);
  });

  it.each([
    ['Deposit', 'deposit'],
    ['Withdraw', 'withdraw'],
  ])('%s opens the Account page cash section with %s selected', async (label, action) => {
    const { page } = await render();

    button(page, label).click();

    expect(router.navigate).toHaveBeenCalledWith(['/account'], {
      queryParams: { section: 'cash', action },
    });
  });
});
