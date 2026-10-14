import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { NgForm } from '@angular/forms';
import { provideRouter } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
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
  async function render() {
    const fixture = TestBed.createComponent(OrdersComponent);
    const component = fixture.componentInstance;
    component.refresh();
    await fixture.whenStable();
    const form = fixture.debugElement.query(By.directive(NgForm)).injector.get(NgForm);
    return { fixture, component, form, page: fixture.nativeElement as HTMLElement };
  }
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
  it('reserves cash for pending buy orders', async () => {
    service.orders.mockReturnValue(of([{ ...order, quantity: 9 }]));
    const { component, form } = await render();
    component.quantity = 2;
    component.submit(form);
    expect(component.availableCash()).toBe(100);
    expect(component.error()).toContain('exceeds your available cash');
    expect(service.place).not.toHaveBeenCalled();
  });
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
    expect(page.textContent).toContain('All account orders');
  });
  it('omits admin controls for regular users', async () => {
    const { page } = await render();
    expect(page.querySelector('[aria-label="Filter account"]')).toBeNull();
    expect(page.textContent).toContain('Your orders');
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
