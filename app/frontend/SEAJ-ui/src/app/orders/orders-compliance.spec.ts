// Covers pre-trade compliance on the order ticket: blocked orders, warnings that need acknowledging,
// resending with the same submission key, and keyboard focus when the confirm button is disabled.
import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { NgForm } from '@angular/forms';
import { provideRouter } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { OrdersService } from './orders.service';
import { OrdersComponent, complianceStop } from './orders';
import { Order } from './orders.models';

const account = { id: 1, accountId: 'ACC-0001', holderName: 'Jay', cashBalance: 10000, status: 'ACTIVE' as const };
const order: Order = {
  orderId: 'order-1',
  accountId: 'ACC-0001',
  accountHolderName: 'Jay',
  ticker: 'AAPL',
  instrumentName: 'Apple',
  side: 'BUY',
  quantity: 1,
  price: 100,
  totalValue: 100,
  orderType: 'MARKET',
  executionPrice: null,
  status: 'PENDING',
  createdAt: '2026-10-19T10:00:00',
};

function stopped(code: 'COMPLIANCE_BLOCKED' | 'COMPLIANCE_WARNING', rule: string, message: string) {
  return new HttpErrorResponse({
    status: 422,
    error: { status: 422, code, message, reasons: [{ rule, outcome: code === 'COMPLIANCE_BLOCKED' ? 'BLOCK' : 'WARN', message }] },
  });
}

describe('Order ticket compliance', () => {
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
  const auth = {
    getToken: vi.fn(() => `header.${btoa(JSON.stringify({ sub: '1', roles: ['USER'] }))}.signature`),
    logout: vi.fn(),
  };

  beforeEach(async () => {
    Object.values(service).forEach((mock) => mock.mockReset());
    service.setup.mockReturnValue(
      of({
        accounts: [{ ...account, name: 'Jay' }],
        instruments: [{ symbol: 'AAPL', name: 'Apple', assetClass: 'EQUITY', currency: 'USD', tradable: true }],
      }),
    );
    service.orders.mockReturnValue(of([]));
    service.portfolio.mockReturnValue(of({ account, positions: [] }));
    service.quote.mockReturnValue(
      of({ symbol: 'AAPL', bidPrice: 99, askPrice: 100, lastPrice: 99.5, priceTimeStamp: '2026-10-19T10:00:00Z' }),
    );
    service.marketOverview.mockReturnValue(
      of({ configured: false, feed: 'iex', updatedAt: null, message: '', snapshots: [], unsupportedSymbols: [] }),
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
    const page = fixture.nativeElement as HTMLElement;
    document.body.appendChild(page);
    const confirm = () => page.querySelector<HTMLButtonElement>('.ticket button[type="submit"]')!;
    return { fixture, component, form, page, confirm };
  }

  /** Review, then confirm: the two steps that send the order */
  async function reviewAndConfirm(view: Awaited<ReturnType<typeof render>>) {
    view.component.submit(view.form);
    view.component.submit(view.form);
    await view.fixture.whenStable();
  }

  afterEach(() => document.body.querySelectorAll('app-orders, [ng-version]').forEach((el) => el.remove()));

  it('shows a blocked order with its reason and keeps confirm disabled until the order changes', async () => {
    service.place.mockReturnValue(
      throwError(() => stopped('COMPLIANCE_BLOCKED', 'RESTRICTED_LIST', "You can't buy AAPL. It's on the restricted list: review")),
    );
    const view = await render();
    await reviewAndConfirm(view);

    const box = view.page.querySelector('#compliance-result')!;
    expect(box.getAttribute('role')).toBe('alert');
    expect(box.textContent).toContain("This order can't be placed");
    expect(box.textContent).toContain("You can't buy AAPL. It's on the restricted list: review");
    expect(box.querySelector('input[type="checkbox"]')).toBeNull();
    expect(view.confirm().disabled).toBe(true);
    expect(view.component.error()).toBe('');

    // Submitting again without changes doesn't resend a blocked order
    view.component.submit(view.form);
    expect(service.place).toHaveBeenCalledTimes(1);

    view.component.edited();
    await view.fixture.whenStable();
    expect(view.page.querySelector('#compliance-result')).toBeNull();
  });

  it('needs a warning acknowledged, then resends the same order with the warning and the same key', async () => {
    service.place.mockReturnValueOnce(
      throwError(() => stopped('COMPLIANCE_WARNING', 'SINGLE_STOCK_LIMIT', 'After this order, AAPL would be 32% of your portfolio.')),
    );
    service.place.mockReturnValueOnce(of(order));
    const view = await render();
    await reviewAndConfirm(view);

    const box = view.page.querySelector('#compliance-result')!;
    expect(box.textContent).toContain('Check before you place this order');
    expect(box.textContent).toContain('32% of your portfolio');
    expect(view.confirm().disabled).toBe(true);

    const tick = box.querySelector<HTMLInputElement>('input[type="checkbox"]')!;
    tick.click();
    await view.fixture.whenStable();
    expect(view.confirm().disabled).toBe(false);
    expect(view.confirm().textContent).toContain('Place buy order anyway');

    view.confirm().click();
    await view.fixture.whenStable();
    const [first, second] = service.place.mock.calls.map((call) => call[0]);
    expect(first.acknowledgedWarnings).toBeUndefined();
    expect(second.acknowledgedWarnings).toEqual(['SINGLE_STOCK_LIMIT']);
    expect(second.idempotencyKey).toBe(first.idempotencyKey);
    expect(view.page.querySelector('#compliance-result')).toBeNull();
    expect(view.component.notice()).toContain('Buy order submitted');
  });

  it('moves keyboard focus to the checkbox, since the disabled confirm button loses it', async () => {
    service.place.mockReturnValue(
      throwError(() => stopped('COMPLIANCE_WARNING', 'RISK_PROFILE', 'Orders above $25,000.00 are outside a balanced risk profile.')),
    );
    const view = await render();
    await reviewAndConfirm(view);
    await new Promise((resolve) => setTimeout(resolve));
    expect(document.activeElement).toBe(view.page.querySelector('#compliance-result input[type="checkbox"]'));
  });

  it('moves focus to the explanation when the order is blocked', async () => {
    service.place.mockReturnValue(throwError(() => stopped('COMPLIANCE_BLOCKED', 'CASH_AVAILABLE', 'Not enough cash.')));
    const view = await render();
    await reviewAndConfirm(view);
    await new Promise((resolve) => setTimeout(resolve));
    expect(document.activeElement?.id).toBe('compliance-result');
  });

  it('going back to editing clears the result', async () => {
    service.place.mockReturnValue(throwError(() => stopped('COMPLIANCE_WARNING', 'RISK_PROFILE', 'Large order.')));
    const view = await render();
    await reviewAndConfirm(view);
    view.component.backToEditing();
    await view.fixture.whenStable();
    expect(view.page.querySelector('#compliance-result')).toBeNull();
    expect(view.component.acknowledged).toBe(false);
  });

  it('shows other errors in the usual alert', async () => {
    service.place.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 400, error: { message: 'Enter a positive limit price' } })),
    );
    const view = await render();
    await reviewAndConfirm(view);
    expect(view.component.error()).toBe('Enter a positive limit price');
    expect(view.page.querySelector('#compliance-result')).toBeNull();
  });

  it('only treats a 422 with compliance reasons as a compliance stop', () => {
    expect(complianceStop(new HttpErrorResponse({ status: 422, error: { code: 'SOMETHING_ELSE', reasons: [] } }))).toBeNull();
    expect(complianceStop(new HttpErrorResponse({ status: 400, error: { code: 'COMPLIANCE_BLOCKED', reasons: [{}] } }))).toBeNull();
    expect(complianceStop(new HttpErrorResponse({ status: 422, error: { code: 'COMPLIANCE_WARNING', reasons: [] } }))).toBeNull();
    expect(complianceStop(null)).toBeNull();
  });
});
