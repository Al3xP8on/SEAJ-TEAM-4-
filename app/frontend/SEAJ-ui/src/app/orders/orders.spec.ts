import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { NgForm } from '@angular/forms';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, Subject, of, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { OrdersService } from './orders.service';
import { OrdersComponent } from './orders';
import { Order, OrderHistory } from './orders.models';

const account = {
  id: 1,
  accountId: 'ACC-0001',
  holderName: 'Jay',
  cashBalance: 1000,
  status: 'ACTIVE' as const,
};
const order: Order = {
  orderId: 'order-1',
  accountId: 'ACC-0001',
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
  createdAt: '2026-10-14T10:00:00',
};
const quote = {
  symbol: 'AAPL',
  bidPrice: 99,
  askPrice: 100,
  lastPrice: 99.5,
  priceTimeStamp: '2026-10-14T10:00:00Z',
};

describe('OrdersComponent', () => {
  const service = {
    setup: vi.fn(),
    orders: vi.fn(),
    portfolio: vi.fn(),
    quote: vi.fn(),
    place: vi.fn(),
    cancel: vi.fn(),
    reject: vi.fn(),
    history: vi.fn(),
    marketOverview: vi.fn(),
    marketBars: vi.fn(),
  };
  const auth = { getToken: vi.fn(), logout: vi.fn() };
  const token = (roles: string[]) =>
    `header.${btoa(JSON.stringify({ sub: '1', roles }))}.signature`;
  beforeEach(async () => {
    Object.values(service).forEach((mock) => mock.mockReset());
    auth.getToken.mockReturnValue(token(['USER']));
    service.setup.mockReturnValue(
      of({
        accounts: [{ ...account, name: 'Jay' }],
        instruments: [
          { symbol: 'AAPL', name: 'Apple', assetClass: 'EQUITY', currency: 'USD', tradable: true },
        ],
      }),
    );
    service.orders.mockReturnValue(of([]));
    service.portfolio.mockReturnValue(
      of({
        account,
        positions: [
          { positionId: 1, symbol: 'AAPL', quantity: 5, averageCost: 95, status: 'OPEN' },
        ],
      }),
    );
    service.quote.mockReturnValue(of(quote));
    service.marketOverview.mockReturnValue(
      of({
        configured: false,
        feed: 'iex',
        updatedAt: null,
        message: 'Alpaca market data is not connected yet.',
        snapshots: [],
        unsupportedSymbols: [],
      }),
    );
    service.place.mockReturnValue(of(order));
    service.cancel.mockReturnValue(of({ ...order, status: 'CANCELLED' }));
    service.history.mockReturnValue(
      of([{ orderId: order.orderId, status: 'PENDING', timestamp: order.createdAt, reason: null }]),
    );
    await TestBed.configureTestingModule({
      imports: [OrdersComponent],
      providers: [
        provideRouter([]),
        { provide: OrdersService, useValue: service },
        { provide: AuthService, useValue: auth },
      ],
    }).compileComponents();
  });
  it('prefills the requested instrument after catalogue loading and fetches its fresh quote', async () => {
    const params = new BehaviorSubject(convertToParamMap({ symbol: 'MSFT' }));
    TestBed.overrideProvider(ActivatedRoute, {
      useValue: { queryParamMap: params.asObservable() },
    });
    const setup = new Subject<any>();
    service.setup.mockReturnValue(setup);
    const fixture = TestBed.createComponent(OrdersComponent);
    fixture.detectChanges();
    expect(service.quote).not.toHaveBeenCalled();
    setup.next({
      accounts: [{ ...account, name: 'Jay' }],
      instruments: [
        { symbol: 'AAPL', name: 'Apple', assetClass: 'Equity', currency: 'USD', tradable: true },
        {
          symbol: 'MSFT',
          name: 'Microsoft',
          assetClass: 'Equity',
          currency: 'USD',
          tradable: true,
        },
      ],
    });
    setup.complete();
    await new Promise((resolve) => setTimeout(resolve, 0));
    await fixture.whenStable();
    expect(fixture.componentInstance.symbol).toBe('MSFT');
    expect(fixture.componentInstance.marketSymbol()).toBe('MSFT');
    expect(service.quote).toHaveBeenCalledWith('MSFT');
    expect(service.place).not.toHaveBeenCalled();
    params.next(convertToParamMap({ symbol: 'AAPL' }));
    expect(fixture.componentInstance.symbol).toBe('AAPL');
    expect(service.quote).toHaveBeenLastCalledWith('AAPL');
  });
  it('ignores unknown instrument query parameters', async () => {
    TestBed.overrideProvider(ActivatedRoute, {
      useValue: { queryParamMap: of(convertToParamMap({ symbol: 'UNKNOWN' })) },
    });
    const { component } = await render();
    expect(component.symbol).toBe('AAPL');
    expect(service.quote).not.toHaveBeenCalledWith('UNKNOWN');
  });
  async function render() {
    const fixture = TestBed.createComponent(OrdersComponent);
    const component = fixture.componentInstance;
    component.refresh();
    await fixture.whenStable();
    const form = fixture.debugElement.query(By.directive(NgForm)).injector.get(NgForm);
    return { fixture, component, form, page: fixture.nativeElement as HTMLElement };
  }
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
    vi.useRealTimers();
  });
  it('pauses the trading desk while hidden and refreshes every ten seconds when visible', async () => {
    const { fixture } = await render();
    vi.useFakeTimers();
    let hidden = true;
    vi.spyOn(document, 'hidden', 'get').mockImplementation(() => hidden);
    document.dispatchEvent(new Event('visibilitychange'));
    const calls = service.orders.mock.calls.length;
    await vi.advanceTimersByTimeAsync(30_000);
    expect(service.orders).toHaveBeenCalledTimes(calls);
    hidden = false;
    document.dispatchEvent(new Event('visibilitychange'));
    await vi.advanceTimersByTimeAsync(1);
    expect(service.orders).toHaveBeenCalledTimes(calls + 1);
    await vi.advanceTimersByTimeAsync(9_998);
    expect(service.orders).toHaveBeenCalledTimes(calls + 1);
    await vi.advanceTimersByTimeAsync(1);
    expect(service.orders).toHaveBeenCalledTimes(calls + 2);
    fixture.destroy();
  });
  it('submits a confirmed buy when randomUUID is unavailable on an HTTP origin', async () => {
    vi.stubGlobal('crypto', {
      getRandomValues: globalThis.crypto.getRandomValues.bind(globalThis.crypto),
    });
    const { component, form } = await render();
    component.submit(form);
    expect(component.review).toBe(true);
    expect(() => component.submit(form)).not.toThrow();
    expect(service.place).toHaveBeenCalledWith(
      expect.objectContaining({
        side: 'BUY',
        idempotencyKey: expect.stringMatching(
          /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/,
        ),
      }),
    );
  });
  it('submits from the confirm button on HTTP and reuses its key for a network retry', async () => {
    const randomValues = vi.fn(globalThis.crypto.getRandomValues.bind(globalThis.crypto));
    vi.stubGlobal('crypto', { getRandomValues: randomValues });
    service.place.mockReturnValueOnce(throwError(() => ({ status: 0 }))).mockReturnValue(of(order));
    const { fixture, page } = await render();
    const button = page.querySelector<HTMLButtonElement>('.ticket button[type="submit"]')!;
    button.click();
    await fixture.whenStable();
    expect(button.textContent).toContain('Confirm buy order');
    button.click();
    await fixture.whenStable();
    expect(page.querySelector('[role="alert"]')?.textContent).toContain('Order submission failed');
    button.click();
    await fixture.whenStable();
    expect(service.place).toHaveBeenCalledTimes(2);
    expect(service.place.mock.calls[0][0].idempotencyKey).toBe(
      service.place.mock.calls[1][0].idempotencyKey,
    );
    expect(randomValues).toHaveBeenCalledTimes(1);
    expect(page.querySelector('.notice')?.textContent).toContain('Buy order submitted');
  });
  it('generates different keys for separate HTTP submissions', async () => {
    vi.stubGlobal('crypto', {
      getRandomValues: globalThis.crypto.getRandomValues.bind(globalThis.crypto),
    });
    const { component, form } = await render();
    component.submit(form);
    component.submit(form);
    component.submit(form);
    component.submit(form);
    expect(service.place).toHaveBeenCalledTimes(2);
    expect(service.place.mock.calls[0][0].idempotencyKey).not.toBe(
      service.place.mock.calls[1][0].idempotencyKey,
    );
  });
  it('shows an error instead of silently failing when browser crypto is unavailable', async () => {
    vi.stubGlobal('crypto', undefined);
    const { fixture, component, page } = await render();
    const button = page.querySelector<HTMLButtonElement>('.ticket button[type="submit"]')!;
    button.click();
    await fixture.whenStable();
    button.click();
    await fixture.whenStable();
    expect(page.querySelector('[role="alert"]')?.textContent).toContain(
      'Could not prepare your order',
    );
    expect(component.submitting()).toBe(false);
    expect(service.place).not.toHaveBeenCalled();
  });
  it('requires a review before placing a market order and leaves pricing to the server', async () => {
    const { component, form } = await render();
    component.submit(form);
    expect(component.review).toBe(true);
    expect(service.place).not.toHaveBeenCalled();
    component.submit(form);
    expect(service.place).toHaveBeenCalledWith(
      expect.objectContaining({
        accountId: 'ACC-0001',
        symbol: 'AAPL',
        side: 'BUY',
        quantity: 1,
        orderType: 'MARKET',
        timeInForce: 'GTC',
        idempotencyKey: expect.any(String),
      }),
    );
    expect(service.place.mock.calls[0][0]).not.toHaveProperty('price');
  });
  it('submits the entered limit price', async () => {
    const { fixture, component, form } = await render();
    component.orderType = 'LIMIT';
    component.limitPrice = 98;
    await fixture.whenStable();
    component.submit(form);
    component.submit(form);
    expect(service.place).toHaveBeenCalledWith(
      expect.objectContaining({ price: 98, orderType: 'LIMIT' }),
    );
  });
  it('requires a fresh review and submits a sell after changing direction with the ticket buttons', async () => {
    service.place.mockReturnValue(of({ ...order, side: 'SELL', quantity: 1, orderType: 'MARKET' }));
    const { fixture, component, page } = await render();
    const submit = page.querySelector<HTMLButtonElement>('.ticket button[type="submit"]')!;
    submit.click();
    await fixture.whenStable();
    expect(submit.textContent).toContain('Confirm buy order');

    page.querySelector<HTMLButtonElement>('.sell-option')!.click();
    await fixture.whenStable();
    expect(component.review).toBe(false);
    expect(submit.textContent).toContain('Review sell order');
    expect(page.querySelector('.selected-quote')?.textContent).toContain('Bid');
    expect(service.place).not.toHaveBeenCalled();

    submit.click();
    await fixture.whenStable();
    submit.click();
    await fixture.whenStable();
    expect(service.place).toHaveBeenCalledExactlyOnceWith(
      expect.objectContaining({ side: 'SELL', quantity: 1, orderType: 'MARKET' }),
    );
    expect(page.querySelector('.notice')?.textContent).toContain('Sell order submitted');
  });
  it('reserves cash for pending buy orders', async () => {
    service.orders.mockReturnValue(of([{ ...order, quantity: 9 }]));
    const { component, form } = await render();
    component.quantity = 2;
    component.submit(form);
    expect(component.availableCash()).toBe(100);
    expect(component.error()).toContain('exceeds your available cash');
    expect(service.place).not.toHaveBeenCalled();
  });
  it('reconciles the cash card and counts only this account’s pending buys', async () => {
    service.portfolio.mockReturnValue(
      of({ account: { ...account, cashBalance: 7899.83 }, positions: [] }),
    );
    service.orders.mockReturnValue(
      of([
        {
          ...order,
          orderId: 'bond-buy',
          ticker: 'UST10',
          status: 'NEW',
          quantity: 60,
          price: 97.5,
        },
        { ...order, orderId: 'apple-buy', quantity: 1, price: 100 },
        { ...order, orderId: 'bank-buy', ticker: 'BAC', quantity: 1, price: 4.98 },
        { ...order, orderId: 'pending-sell', side: 'SELL', quantity: 1, price: 5000 },
        { ...order, orderId: 'filled-buy', status: 'FILLED', quantity: 1, price: 5000 },
        { ...order, orderId: 'other-account', accountId: 'ACC-0002', quantity: 1, price: 5000 },
      ]),
    );
    const { component, page } = await render();
    expect(component.reservedCash()).toBe(5954.98);
    expect(component.availableCash()).toBe(1944.85);
    expect(page.querySelector('.cash')?.textContent).toContain('$7,899.83');
    expect(page.querySelector('.cash-reserved')?.textContent).toContain('$5,954.98');
    expect(page.querySelector('.cash-available')?.textContent).toContain('$1,944.85');
  });
  it.each(['FILLED', 'CANCELLED', 'REJECTED'] as const)(
    'releases reserved cash when a buy becomes %s',
    async (status) => {
      service.orders.mockReturnValue(of([{ ...order, quantity: 9 }]));
      const { fixture, component, page } = await render();
      expect(component.reservedCash()).toBe(900);
      const cashBalance = status === 'FILLED' ? 100 : 1000;
      service.orders.mockReturnValue(of([{ ...order, quantity: 9, status }]));
      service.portfolio.mockReturnValue(
        of({ account: { ...account, cashBalance }, positions: [] }),
      );
      component.refresh();
      await fixture.whenStable();
      expect(component.reservedCash()).toBe(0);
      expect(component.availableCash()).toBe(cashBalance);
      expect(page.querySelector('.cash-reserved')?.textContent).toContain('$0.00');
    },
  );
  it('prevents selling holdings reserved by another pending sell', async () => {
    service.orders.mockReturnValue(of([{ ...order, side: 'SELL', quantity: 4 }]));
    const { component, form } = await render();
    component.side = 'SELL';
    component.quantity = 2;
    component.submit(form);
    expect(component.availableHolding()).toBe(1);
    expect(component.error()).toContain('available holdings');
  });
  it('reuses the submission key when retrying an ambiguous network failure', async () => {
    service.place.mockReturnValue(throwError(() => ({ status: 0 })));
    const { component, form } = await render();
    component.submit(form);
    component.submit(form);
    component.submit(form);
    expect(service.place).toHaveBeenCalledTimes(2);
    expect(service.place.mock.calls[0][0].idempotencyKey).toBe(
      service.place.mock.calls[1][0].idempotencyKey,
    );
  });
  it('does not offer cancellation on filled orders', async () => {
    service.orders.mockReturnValue(of([{ ...order, status: 'FILLED', executionPrice: 99 }]));
    const { page } = await render();
    expect(page.querySelector('.row-actions')?.textContent).toContain('Details');
    expect(page.querySelector('.row-actions')?.textContent).not.toContain('Cancel');
  });
  it('updates cash and holdings when a fill is observed', async () => {
    const { fixture, component, page } = await render();
    service.orders.mockReturnValue(of([{ ...order, status: 'FILLED', executionPrice: 99 }]));
    service.portfolio.mockReturnValue(
      of({
        account: { ...account, cashBalance: 802 },
        positions: [
          { positionId: 1, symbol: 'AAPL', quantity: 7, averageCost: 96.14, status: 'OPEN' },
        ],
      }),
    );
    component.refresh();
    await fixture.whenStable();
    expect(page.querySelector('.cash')?.textContent).toContain('$802.00');
    expect(component.positions()[0].quantity).toBe(7);
    expect(page.querySelector('[data-status="FILLED"]')).not.toBeNull();
  });
  it('shows an account filter for admins', async () => {
    auth.getToken.mockReturnValue(token(['ADMIN', 'USER']));
    const { page } = await render();
    expect(page.querySelector('[aria-label="Filter account"]')).not.toBeNull();
    expect(page.querySelector('#account')).not.toBeNull();
    expect(page.textContent).toContain('All account orders');
  });
  it('omits admin controls for regular users', async () => {
    const { page } = await render();
    expect(page.querySelector('[aria-label="Filter account"]')).toBeNull();
    expect(page.querySelector('#account')).toBeNull();
    expect(page.querySelector('label[for="account"]')).toBeNull();
    expect(page.textContent).toContain('Your orders');
  });
  it('searches symbols and names, and selects an instrument with the keyboard', async () => {
    service.setup.mockReturnValue(
      of({
        accounts: [{ ...account, name: 'Jay' }],
        instruments: [
          {
            symbol: 'AAPL',
            name: 'Apple Inc.',
            assetClass: 'EQUITY',
            currency: 'USD',
            tradable: true,
          },
          {
            symbol: 'MSFT',
            name: 'Microsoft',
            assetClass: 'EQUITY',
            currency: 'USD',
            tradable: true,
          },
        ],
      }),
    );
    const { fixture, component, page } = await render();
    const input = page.querySelector<HTMLInputElement>('#instrument')!;
    expect(page.querySelector('.instrument-detail')).toBeNull();
    expect(page.querySelector('.portfolio .market-overview')).not.toBeNull();
    expect(page.querySelector('.ticket-column .market-overview')).toBeNull();
    input.focus();
    input.value = ' MICRO ';
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    expect(page.querySelectorAll('[role="option"]')).toHaveLength(1);
    expect(page.querySelector('[role="option"]')?.textContent).toContain('MSFT');
    expect(page.querySelector('.portfolio .market-overview')).not.toBeNull();
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    await fixture.whenStable();
    expect(component.symbol).toBe('MSFT');
    expect(service.quote).toHaveBeenLastCalledWith('MSFT');
    expect(input.getAttribute('aria-expanded')).toBe('false');
    expect(page.querySelector('.instrument-detail')?.textContent).toContain('MSFT');
    const ticketColumn = page.querySelector('.ticket-column')!;
    expect(ticketColumn.children[0].classList.contains('ticket')).toBe(true);
    expect(ticketColumn.children[1].classList.contains('market-overview')).toBe(true);
    expect(page.querySelector('.portfolio .instrument-detail')?.textContent).toContain('MSFT');
    expect(page.querySelector('.portfolio .market-overview')).toBeNull();
    expect(page.querySelectorAll('.market-overview')).toHaveLength(1);
    expect(page.querySelectorAll('app-market-data')).toHaveLength(1);
    input.focus();
    component.openInstrumentPicker();
    input.value = 'aapl';
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    expect(page.querySelector('[role="option"]')?.textContent).toContain('AAPL');
  });
  it('keeps the current instrument when a search has no matches or is dismissed', async () => {
    const { fixture, component, page } = await render();
    const input = page.querySelector<HTMLInputElement>('#instrument')!;
    input.focus();
    input.value = 'not-a-symbol';
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    expect(page.textContent).toContain('No instruments match your search.');
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    expect(component.symbol).toBe('AAPL');
    expect(component.review).toBe(false);
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    await fixture.whenStable();
    expect(input.value).toContain('AAPL');
    expect(page.querySelector('[role="listbox"]')).toBeNull();
  });
  it('does not substitute another trading account for a regular user', async () => {
    service.setup.mockReturnValue(
      of({
        accounts: [{ ...account, id: 2, accountId: 'ACC-0002', name: 'Another user' }],
        instruments: [],
      }),
    );
    const fixture = TestBed.createComponent(OrdersComponent);
    await fixture.whenStable();
    expect(fixture.componentInstance.accountId).toBe('');
    expect(fixture.componentInstance.error()).toContain('No trading account');
    expect(service.portfolio).not.toHaveBeenCalled();
  });
  it('keeps a cancellation error visible if a fill wins the race', async () => {
    service.cancel.mockReturnValue(throwError(() => ({ status: 400 })));
    const { component } = await render();
    component.confirming.set(order);
    component.cancel(order);
    expect(component.error()).toContain('may already have filled');
    expect(component.confirming()).toEqual(order);
  });
  it('shows the saved reason when opening a rejected order', async () => {
    const rejected: Order = { ...order, status: 'REJECTED' };
    const reason = 'Insufficient cash at execution: required $1200.00, available $1000.00.';
    service.orders.mockReturnValue(of([rejected]));
    service.history.mockReturnValue(
      of([{ orderId: order.orderId, status: 'REJECTED', timestamp: order.createdAt, reason }]),
    );
    const { fixture, page } = await render();
    const details = page.querySelector<HTMLButtonElement>('.row-actions button')!;
    expect(details.textContent).toContain('View reason');
    details.click();
    await fixture.whenStable();
    expect(page.querySelector('.rejection-reason')?.textContent).toContain(reason);
  });

  it.each([
    { history: [] },
    {
      history: [
        {
          orderId: order.orderId,
          status: 'REJECTED' as const,
          timestamp: order.createdAt,
          reason: null,
        },
      ],
    },
  ])('explains when a rejected order has no recorded reason', async ({ history }) => {
    const rejected: Order = { ...order, status: 'REJECTED' };
    service.history.mockReturnValue(of(history));
    const { fixture, component, page } = await render();
    component.showHistory(rejected);
    await fixture.whenStable();
    expect(page.querySelector('.rejection-reason')?.textContent).toContain(
      'No rejection reason was recorded for this order.',
    );
  });

  it('refreshes an open timeline when its order is rejected and ignores an older response', async () => {
    const earlier = new Subject<OrderHistory[]>();
    const rejected: Order = { ...order, status: 'REJECTED' };
    const reason = 'Instrument AAPL is not tradable.';
    service.orders.mockReturnValue(of([order]));
    service.history
      .mockReturnValueOnce(earlier)
      .mockReturnValue(
        of([{ orderId: order.orderId, status: 'REJECTED', timestamp: order.createdAt, reason }]),
      );
    const { fixture, component, page } = await render();
    component.showHistory(order);
    service.orders.mockReturnValue(of([rejected]));
    component.refresh();
    await fixture.whenStable();
    expect(page.querySelector('.rejection-reason')?.textContent).toContain(reason);
    earlier.next([
      { orderId: order.orderId, status: 'PENDING', timestamp: order.createdAt, reason: null },
    ]);
    earlier.complete();
    await fixture.whenStable();
    expect(page.querySelector('.rejection-reason')?.textContent).toContain(reason);
  });

  it('shows a loading failure separately from a missing historical reason', async () => {
    service.history.mockReturnValue(throwError(() => ({ status: 503 })));
    const { fixture, component, page } = await render();
    component.showHistory({ ...order, status: 'REJECTED' });
    await fixture.whenStable();
    expect(page.textContent).toContain('Could not load this order’s timeline.');
    expect(page.querySelector('.rejection-reason')).toBeNull();
  });
  it('labels the old generic rejection message without guessing its specific cause', async () => {
    service.history.mockReturnValue(
      of([
        {
          orderId: order.orderId,
          status: 'REJECTED',
          timestamp: order.createdAt,
          reason:
            'Account inactive, instrument unavailable, or insufficient cash/holdings at execution',
        },
      ]),
    );
    const { fixture, component, page } = await render();
    component.showHistory({ ...order, status: 'REJECTED' });
    await fixture.whenStable();
    expect(page.querySelector('.rejection-reason')?.textContent).toContain(
      'A specific rejection reason was not recorded for this order.',
    );
  });
  it('returns keyboard focus to the order action after a live rejection updates its dialog', async () => {
    service.orders.mockReturnValue(of([order]));
    const { fixture, component, page } = await render();
    const action = page.querySelector<HTMLButtonElement>('.row-actions button')!;
    action.focus();
    action.click();
    await fixture.whenStable();
    service.orders.mockReturnValue(of([{ ...order, status: 'REJECTED' }]));
    service.history.mockReturnValue(
      of([
        {
          orderId: order.orderId,
          status: 'REJECTED',
          timestamp: order.createdAt,
          reason: 'Account is SUSPENDED.',
        },
      ]),
    );
    component.refresh();
    await fixture.whenStable();
    component.historyOrder.set(null);
    await fixture.whenStable();
    expect(document.activeElement).toBe(action);
  });
});
