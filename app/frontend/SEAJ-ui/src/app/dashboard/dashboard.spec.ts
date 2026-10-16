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
      '▲ +$50.00 (+0.29%) today',
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

  it('shows a message instead of the table when there are no holdings', async () => {
    const closedOnly: Position[] = [
      { positionId: 9, symbol: 'TSLA', quantity: 0, averageCost: 240, status: 'CLOSED' },
    ];
    dashboard.load.mockReturnValue(of({ ...DATA, positions: closedOnly }));
    const { page } = await render();

    expect(page.querySelector('.positions-table')).toBeNull();
    expect(page.querySelector('.empty')?.textContent).toContain("don't hold any positions");
  });

  it('lists the five most recent orders, newest first', async () => {
    const { page } = await render();
    const rows = Array.from(page.querySelectorAll('.order-list .order'));

    // o1 (AAPL, the oldest) drops off
    expect(rows.map((r) => r.querySelector('.symbol')?.textContent)).toEqual([
      'MSFT',
      'META',
      'AMZN',
      'TSLA',
      'NVDA',
    ]);
    expect(rows[0].textContent).toContain('SELL');
    expect(rows[0].querySelector('.badge')?.textContent).toBe('PENDING');
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

  it('passes holdings, totals and reserved cash to the portfolio check', async () => {
    const { page } = await render();

    // AAPL is 31.2% of the $29,203.25 portfolio and MSFT 28.4%, both above 25%
    const warnings = Array.from(page.querySelectorAll('app-portfolio-check .check.warning p'));
    expect(warnings.map((p) => p.textContent?.trim().split(' ')[0])).toEqual(['AAPL', 'MSFT']);
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
    ['Order History', '/blotter'],
    ['View all in blotter', '/blotter'],
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
