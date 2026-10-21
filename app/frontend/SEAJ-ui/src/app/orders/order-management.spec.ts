import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { OrdersComponent } from './orders';
import { OrdersService } from './orders.service';
import { Order } from './orders.models';

describe('Order management', () => {
  const service = {
    managementSetup: vi.fn(),
    setup: vi.fn(),
    orders: vi.fn(),
    portfolio: vi.fn(),
    quote: vi.fn(),
    cancel: vi.fn(),
    history: vi.fn(),
    place: vi.fn(),
    marketOverview: vi.fn(),
  };
  const account = {
    id: 1,
    accountId: 'ACC-0001',
    name: 'Jay',
    status: 'ACTIVE',
    cashBalance: 1000,
  };
  let orders: Order[];
  const daysAgo = (days: number) => {
    const date = new Date();
    date.setDate(date.getDate() - days);
    return date.toISOString();
  };

  beforeEach(async () => {
    Object.values(service).forEach((mock) => mock.mockReset());
    const base: Order = {
      orderId: 'pending',
      accountId: account.accountId,
      accountHolderName: 'Jay',
      ticker: 'AAPL',
      instrumentName: 'Apple',
      side: 'BUY',
      quantity: 2,
      price: 100,
      totalValue: 200,
      orderType: 'LIMIT',
      executionPrice: null,
      status: 'PENDING',
      createdAt: daysAgo(0),
    };
    orders = [
      base,
      { ...base, orderId: 'filled', status: 'FILLED', executionPrice: 98, createdAt: daysAgo(1) },
      { ...base, orderId: 'cancelled', status: 'CANCELLED', createdAt: daysAgo(10) },
      { ...base, orderId: 'rejected', status: 'REJECTED', createdAt: daysAgo(40) },
      { ...base, orderId: 'new', status: 'NEW', side: 'SELL', quantity: 1, createdAt: daysAgo(0) },
    ];
    service.managementSetup.mockReturnValue(of({ accounts: [account], instruments: [] }));
    service.setup.mockReturnValue(
      of({
        accounts: [account],
        instruments: [
          { symbol: 'AAPL', name: 'Apple', tradable: true, currency: 'USD', assetClass: 'EQUITY' },
        ],
      }),
    );
    service.orders.mockImplementation(() => of(orders));
    service.portfolio.mockReturnValue(of({ account, positions: [] }));
    service.quote.mockReturnValue(of(null));
    service.marketOverview.mockReturnValue(
      of({ configured: false, snapshots: [], unsupportedSymbols: [], message: '', feed: 'iex' }),
    );
    service.history.mockReturnValue(
      of([
        {
          orderId: 'rejected',
          status: 'REJECTED',
          timestamp: daysAgo(0),
          reason: 'Insufficient cash at execution.',
        },
      ]),
    );
    await TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'blotter', component: OrdersComponent, data: { management: true } },
          { path: 'order', component: OrdersComponent },
        ]),
        { provide: OrdersService, useValue: service },
        {
          provide: AuthService,
          useValue: { getToken: () => `h.${btoa('{"sub":"1","roles":["USER"]}')}.s` },
        },
      ],
    }).compileComponents();
  });

  async function render() {
    const harness = await RouterTestingHarness.create();
    const component = await harness.navigateByUrl('/blotter', OrdersComponent);
    component.refresh();
    harness.detectChanges();
    await harness.fixture.whenStable();
    return { harness, component, page: harness.routeNativeElement! };
  }

  it('opens a focused management page and does not request market data or portfolios', async () => {
    const { component, page } = await render();
    expect(page.querySelector('h1')?.textContent).toBe('Order management');
    expect(page.querySelector('.ticket')).toBeNull();
    expect(page.querySelector('app-market-data')).toBeNull();
    expect(
      component
        .visibleOrders()
        .map((order) => order.orderId)
        .sort(),
    ).toEqual(['new', 'pending']);
    expect(component.committedCash()).toBe(200);
    expect(component.filledOrders()).toHaveLength(1);
    expect(service.setup).not.toHaveBeenCalled();
    expect(service.quote).not.toHaveBeenCalled();
    expect(service.portfolio).not.toHaveBeenCalled();
  });

  it('separates active, completed and rejected orders and resets conflicting status filters', async () => {
    const { component } = await render();
    component.statusFilter.set('PENDING');
    component.selectView('COMPLETED');
    expect(component.visibleOrders().map((order) => order.orderId)).toEqual([
      'filled',
      'cancelled',
    ]);
    component.selectView('REJECTED');
    expect(component.visibleOrders().map((order) => order.orderId)).toEqual(['rejected']);
    component.clearFilters();
    expect(component.visibleOrders()).toHaveLength(5);
  });

  it('lets regular users combine date, direction and search filters', async () => {
    const { component, page } = await render();
    expect(page.querySelector('[aria-label="Filter order date"]')).not.toBeNull();
    expect(page.querySelector('[aria-label="Filter account"]')).toBeNull();
    component.selectView('ALL');
    component.dateFilter.set('WEEK');
    expect(component.visibleOrders()).toHaveLength(3);
    component.dateFilter.set('MONTH');
    expect(component.visibleOrders()).toHaveLength(4);
    component.dateFilter.set('TODAY');
    component.sideFilter.set('SELL');
    component.search.set('aapl');
    expect(component.visibleOrders().map((order) => order.orderId)).toEqual(['new']);
    component.clearFilters();
    expect(component.visibleOrders()).toHaveLength(5);
  });

  it('uses execution prices for filled trade values', async () => {
    const { component } = await render();
    expect(component.orderValue(orders[1])).toBe(196);
    expect(component.orderValue(orders[0])).toBe(200);
    component.selectView('ALL');
    component.sortOrder.set('OLDEST');
    expect(component.visibleOrders()[0].orderId).toBe('rejected');
    component.sortOrder.set('VALUE');
    expect(component.visibleOrders().at(-1)?.orderId).toBe('new');
  });

  it('keeps rejection reasons and execution details accessible', async () => {
    const { harness, component, page } = await render();
    component.showHistory(orders[3]);
    harness.detectChanges();
    expect(page.querySelector('[role="dialog"]')?.textContent).toContain(
      'Insufficient cash at execution.',
    );
    expect(page.querySelector('.order-detail-summary')?.textContent).toContain(
      'Estimated trade value',
    );
    component.showHistory(orders[1]);
    harness.detectChanges();
    expect(page.querySelector('.order-detail-summary')?.textContent).toContain('Cash spent');
    expect(page.querySelector('.order-detail-summary')?.textContent).toContain('$196.00');
  });

  it('disables cancellation if the order fills while the confirmation is open', async () => {
    const { harness, component, page } = await render();
    component.beginCancel(orders[0]);
    orders = orders.map((order) =>
      order.orderId === 'pending' ? { ...order, status: 'FILLED', executionPrice: 99 } : order,
    );
    component.refresh();
    harness.detectChanges();
    expect(page.querySelector('[role="dialog"]')?.textContent).toContain(
      'can no longer be cancelled',
    );
    expect(page.querySelector<HTMLButtonElement>('[role="dialog"] .primary')?.disabled).toBe(true);
    component.cancel(component.confirming()!);
    expect(service.cancel).not.toHaveBeenCalled();
  });

  it('opens a fresh ticket without submitting or copying old quantities and prices', async () => {
    const { harness, page } = await render();
    expect(page.querySelector('a.new-ticket')?.getAttribute('href')).toContain('symbol=AAPL');
    const ticket = await harness.navigateByUrl('/order?symbol=AAPL&side=SELL', OrdersComponent);
    expect(ticket.symbol).toBe('AAPL');
    expect(ticket.side).toBe('SELL');
    expect(ticket.quantity).toBe(1);
    expect(ticket.orderType).toBe('MARKET');
    expect(ticket.review).toBe(false);
    expect(service.place).not.toHaveBeenCalled();
  });

  it('updates cash commitments after a successful cancellation', async () => {
    service.cancel.mockImplementation((id: string) => {
      orders = orders.map((order) =>
        order.orderId === id ? { ...order, status: 'CANCELLED' } : order,
      );
      return of(orders.find((order) => order.orderId === id));
    });
    const { component } = await render();
    component.beginCancel(orders[0]);
    component.cancel(component.confirming()!);
    expect(service.cancel).toHaveBeenCalledExactlyOnceWith('pending');
    expect(component.committedCash()).toBe(0);
    expect(component.activeOrders()).toHaveLength(1);
    expect(component.confirming()).toBeNull();
  });

  it('keeps admin summaries consistent with the selected account', async () => {
    vi.spyOn(TestBed.inject(AuthService), 'getToken').mockReturnValue(
      `h.${btoa('{"sub":"1","roles":["ADMIN"]}')}.s`,
    );
    orders.push({
      ...orders[0],
      accountId: 'ACC-0002',
      orderId: 'other-account',
      quantity: 3,
      price: 150,
    });
    const { component } = await render();
    component.accountFilter.set('ACC-0002');
    expect(component.activeOrders()).toHaveLength(1);
    expect(component.committedCash()).toBe(450);
    expect(component.filledOrders()).toHaveLength(0);
    expect(component.visibleOrders().map((order) => order.orderId)).toEqual(['other-account']);
  });

  it('does not substitute a different instrument when a past instrument is unavailable', async () => {
    const harness = await RouterTestingHarness.create();
    const ticket = await harness.navigateByUrl('/order?symbol=DELISTED&side=BUY', OrdersComponent);
    expect(ticket.symbol).toBe('');
    expect(ticket.error()).toContain('no longer available');
    expect(service.place).not.toHaveBeenCalled();
  });
});
