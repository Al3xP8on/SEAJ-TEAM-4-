import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { AdminComponent } from './admin';
import { AccountDetails, AdminAccount, AdminService } from './admin.service';

const ACCOUNTS: AdminAccount[] = [
  {
    id: 1,
    accountId: 'ACC-0001',
    username: 'jay',
    holderName: 'Jay',
    email: 'jay@example.com',
    cashBalance: 1000,
    roles: 'ADMIN,USER',
    status: 'ACTIVE',
    lastUpdated: '2026-10-16T12:00:00',
  },
  {
    id: 2,
    accountId: 'ACC-0002',
    username: 'brian',
    holderName: 'Brian',
    email: 'brian@example.com',
    cashBalance: 250,
    roles: 'USER',
    status: 'ACTIVE',
    lastUpdated: '2026-10-16T12:00:00',
  },
  {
    id: 3,
    accountId: 'ACC-0003',
    username: 'carla',
    holderName: 'Carla',
    email: 'carla@example.com',
    cashBalance: 0,
    roles: 'USER',
    status: 'SUSPENDED',
    lastUpdated: '2026-10-16T12:00:00',
  },
];
const DETAILS: AccountDetails = {
  positions: [{ positionId: 1, symbol: 'AAPL', quantity: 2, averageCost: 100, status: 'OPEN' }],
  orders: [
    {
      orderId: 'order-1',
      accountId: 'ACC-0002',
      accountHolderName: 'Brian',
      ticker: 'AAPL',
      instrumentName: 'Apple',
      side: 'BUY',
      quantity: 2,
      price: 100,
      executionPrice: 99,
      totalValue: 200,
      orderType: 'MARKET',
      status: 'FILLED',
      createdAt: '2026-10-16T12:00:00',
    },
  ],
};

describe('Admin account management', () => {
  const service = { accounts: vi.fn(), details: vi.fn(), changeStatus: vi.fn(), history: vi.fn() };
  const auth = { getToken: vi.fn(), registerAdmin: vi.fn(), verifyRegistration: vi.fn() };

  beforeEach(async () => {
    vi.resetAllMocks();
    service.accounts.mockReturnValue(of(ACCOUNTS.map((account) => ({ ...account }))));
    service.details.mockReturnValue(of(DETAILS));
    service.history.mockReturnValue(of([]));
    auth.getToken.mockReturnValue(`h.${btoa('{"sub":"1","roles":["ADMIN"]}')}.s`);
    auth.registerAdmin.mockReturnValue(of({ challenge_id: 'test-challenge', expires_in: 600 }));
    auth.verifyRegistration.mockReturnValue(
      of({ username: 'newuser', account_id: 'ACC-NEW', email_verified: true }),
    );
    // jsdom does not implement native dialog methods.
    Object.defineProperty(HTMLDialogElement.prototype, 'showModal', {
      configurable: true,
      value: function (this: HTMLDialogElement) {
        this.open = true;
      },
    });
    Object.defineProperty(HTMLDialogElement.prototype, 'close', {
      configurable: true,
      value: function (this: HTMLDialogElement) {
        this.open = false;
      },
    });
    await TestBed.configureTestingModule({
      imports: [AdminComponent],
      providers: [
        provideRouter([]),
        { provide: AdminService, useValue: service },
        { provide: AuthService, useValue: auth },
      ],
    }).compileComponents();
  });

  afterEach(() => vi.restoreAllMocks());

  async function render() {
    const fixture = TestBed.createComponent(AdminComponent);
    fixture.componentInstance.activeSection.set('accounts');
    await fixture.whenStable();
    return {
      fixture,
      component: fixture.componentInstance,
      page: fixture.nativeElement as HTMLElement,
    };
  }
  function button(page: HTMLElement, text: string) {
    return Array.from(page.querySelectorAll<HTMLButtonElement>('button')).find(
      (button) => button.textContent?.trim() === text,
    )!;
  }
  async function fill(fixture: any, page: HTMLElement, name: string, value: string) {
    const input = page.querySelector<HTMLInputElement>(`input[name="${name}"]`)!;
    input.value = value;
    input.dispatchEvent(new Event('input', { bubbles: true }));
    await fixture.whenStable();
  }

  it('loads account summaries without fetching every account portfolio', async () => {
    const { page } = await render();
    expect(page.querySelector('h1')?.textContent).toContain('Admin dashboard');
    expect(page.querySelector('.stats')?.textContent).toContain('$1,250.00');
    expect(page.querySelectorAll('.accounts-panel tbody tr')).toHaveLength(3);
    expect(service.details).not.toHaveBeenCalled();
  });

  it('searches names, emails and account IDs together with status filters', async () => {
    const { fixture, component, page } = await render();
    component.search.set('BRIAN@');
    await fixture.whenStable();
    expect(page.querySelector('.accounts-panel tbody')?.textContent).toContain('Brian');
    expect(page.querySelector('.accounts-panel tbody')?.textContent).not.toContain('Jay');
    component.status.set('SUSPENDED');
    await fixture.whenStable();
    expect(page.textContent).toContain('No accounts match');
    component.search.set('ACC-0003');
    await fixture.whenStable();
    expect(page.querySelector('.accounts-panel tbody')?.textContent).toContain('Carla');
  });

  it('shows holdings at cost and filled order execution values for a selected account', async () => {
    const { fixture, component, page } = await render();
    component.select(ACCOUNTS[1]);
    await fixture.whenStable();
    expect(service.details).toHaveBeenCalledWith('ACC-0002');
    expect(page.querySelector('.detail-summary')?.textContent).toContain('$200.00');
    expect(page.querySelector('.order-history')?.textContent).toContain('$198.00');
    expect(page.querySelector('.order-history')?.textContent).toContain('filled');
  });

  it('ignores an old detail response when switching accounts', async () => {
    const slow = new Subject<AccountDetails>();
    service.details
      .mockReturnValueOnce(slow)
      .mockReturnValueOnce(of({ positions: [], orders: [] }));
    const { fixture, component } = await render();
    component.select(ACCOUNTS[1]);
    component.select(ACCOUNTS[2]);
    slow.next(DETAILS);
    await fixture.whenStable();
    expect(component.selected()?.id).toBe(3);
    expect(component.details()?.orders).toEqual([]);
    expect(component.detailsLoading()).toBe(false);
  });

  it('confirms suspension before sending a status change and updates the summary', async () => {
    service.changeStatus.mockReturnValue(of({ ...ACCOUNTS[1], status: 'SUSPENDED' }));
    const { fixture, component, page } = await render();
    component.select(ACCOUNTS[1]);
    await fixture.whenStable();
    button(page, 'Suspend account').click();
    await fixture.whenStable();
    expect(service.changeStatus).not.toHaveBeenCalled();
    expect(page.querySelector('dialog')?.open).toBe(true);
    component.reason = 'Account review';
    page.querySelector<HTMLButtonElement>('dialog button.danger')!.click();
    await fixture.whenStable();
    expect(service.changeStatus).toHaveBeenCalledWith(2, 'SUSPENDED', 'Account review');
    expect(component.suspendedCount()).toBe(2);
    expect(page.querySelector('[role="status"]')?.textContent).toContain('now suspended');
    expect(page.querySelector('dialog')?.open).toBe(false);
    expect(button(page, 'Reactivate account')).toBeDefined();
  });

  it('cancels confirmation without changing an account', async () => {
    const { fixture, component, page } = await render();
    component.askStatus(ACCOUNTS[1], 'CLOSED');
    await fixture.whenStable();
    page.querySelector<HTMLButtonElement>('dialog button')!.click();
    expect(service.changeStatus).not.toHaveBeenCalled();
    expect(component.pendingAction()).toBeNull();
  });

  it('keeps a server rejection visible inside the confirmation', async () => {
    service.changeStatus.mockReturnValue(
      throwError(() => ({
        error: { message: 'Withdraw the remaining cash before closing this account.' },
      })),
    );
    const { fixture, component, page } = await render();
    component.askStatus(ACCOUNTS[1], 'CLOSED');
    component.reason = 'Account review';
    component.saveStatus();
    await fixture.whenStable();
    expect(page.querySelector('dialog [role="alert"]')?.textContent).toContain('remaining cash');
    expect(page.querySelector('dialog')?.open).toBe(true);
    expect(component.accounts()[1].status).toBe('ACTIVE');
  });

  it('prevents duplicate submissions and keeps a saving confirmation open', async () => {
    service.changeStatus.mockReturnValue(new Subject());
    const { component } = await render();
    component.askStatus(ACCOUNTS[1], 'SUSPENDED');
    component.reason = 'Account review';
    component.saveStatus();
    component.reason = 'Account review';
    component.saveStatus();
    component.closeConfirmation();
    expect(service.changeStatus).toHaveBeenCalledTimes(1);
    expect(component.pendingAction()).not.toBeNull();
  });

  it('does not offer suspension or closure for the signed-in admin', async () => {
    const { fixture, component, page } = await render();
    component.select(ACCOUNTS[0]);
    await fixture.whenStable();
    expect(page.querySelector('.account-controls')?.textContent).toContain('own admin account');
    expect(page.querySelector('.account-controls button')).toBeNull();
  });

  it('requires email verification to finish creating a normal user account', async () => {
    const { fixture, component, page } = await render();
    button(page, 'Create account').click();
    await fixture.whenStable();
    await fill(fixture, page, 'name', 'New User');
    await fill(fixture, page, 'username', 'newuser');
    await fill(fixture, page, 'email', 'new@example.com');
    await fill(fixture, page, 'password', 'valid-password');
    button(page, 'Send verification code').click();
    await fixture.whenStable();
    expect(auth.registerAdmin).toHaveBeenCalledWith(
      {
        name: 'New User',
        username: 'newuser',
        email: 'new@example.com',
        password: 'valid-password',
      },
      'USER',
    );
    expect(auth.verifyRegistration).not.toHaveBeenCalled();
    expect(component.credentials.password).toBe('');
    expect(page.textContent).toContain('A code was sent to');
    await fill(fixture, page, 'code', '123456');
    button(page, 'Verify and create account').click();
    await fixture.whenStable();
    expect(auth.verifyRegistration).toHaveBeenCalledWith('test-challenge', '123456');
    expect(component.showCreate()).toBe(false);
    expect(component.notice()).toContain('ACC-NEW');
    expect(service.accounts).toHaveBeenCalledTimes(2);
  });

  it('explains duplicate registration errors without losing the form', async () => {
    auth.registerAdmin.mockReturnValue(
      throwError(() => new Error('Email or username already exists')),
    );
    const { fixture, component, page } = await render();
    component.showCreate.set(true);
    await fixture.whenStable();
    await fill(fixture, page, 'name', 'New User');
    await fill(fixture, page, 'username', 'newuser');
    await fill(fixture, page, 'email', 'new@example.com');
    await fill(fixture, page, 'password', 'valid-password');
    button(page, 'Send verification code').click();
    await fixture.whenStable();
    expect(page.querySelector('.create-panel [role="alert"]')?.textContent).toContain(
      'already exists',
    );
    expect(component.challengeId()).toBe('');
  });

  it('opens with an operational overview instead of the full account table', async () => {
    const fixture = TestBed.createComponent(AdminComponent);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    expect(fixture.componentInstance.activeSection()).toBe('overview');
    expect(page.textContent).toContain('Access across the platform');
    expect(page.textContent).toContain('Recently updated accounts');
    expect(page.querySelector('.accounts-panel')).toBeNull();
    expect(service.details).not.toHaveBeenCalled();
  });

  it('switches between account management and the overview without losing account filters', async () => {
    const { fixture, component, page } = await render();
    component.search.set('brian');
    button(page, 'Overview').click();
    await fixture.whenStable();
    expect(page.querySelector('.accounts-panel')).toBeNull();
    component.activeSection.set('accounts');
    await fixture.whenStable();
    expect(component.visibleAccounts()).toHaveLength(1);
    expect(page.querySelector('.accounts-panel')?.textContent).toContain('Brian');
  });

  it('opens a suspended account directly from the overview', async () => {
    const fixture = TestBed.createComponent(AdminComponent);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    page.querySelector<HTMLButtonElement>('.review-account')!.click();
    await fixture.whenStable();
    expect(fixture.componentInstance.activeSection()).toBe('accounts');
    expect(fixture.componentInstance.selected()?.id).toBe(3);
    expect(service.history).toHaveBeenCalledWith(3);
    expect(page.querySelector('.detail-panel')?.textContent).toContain('Carla');
  });

  it('does not display another account’s history after a quick selection change', async () => {
    const oldHistory = new Subject<unknown[]>();
    service.history.mockReturnValueOnce(oldHistory).mockReturnValueOnce(of([]));
    const { component } = await render();
    component.select(ACCOUNTS[1]);
    component.select(ACCOUNTS[2]);
    oldHistory.next([{ id: 1, reason: 'History for Brian' }]);
    expect(component.selectedId()).toBe(3);
    expect(component.statusHistory()).toEqual([]);
    expect(component.historyLoading()).toBe(false);
  });

  it('distinguishes failed audit loading from an empty audit trail', async () => {
    service.history
      .mockReturnValueOnce(throwError(() => new Error('Offline')))
      .mockReturnValueOnce(of([]));
    const { fixture, component, page } = await render();
    component.select(ACCOUNTS[1]);
    await fixture.whenStable();
    expect(page.textContent).toContain('Account history could not be loaded');
    expect(page.textContent).not.toContain('No admin status changes recorded');
    button(page, 'Retry history').click();
    await fixture.whenStable();
    expect(page.textContent).toContain('No admin status changes recorded');
  });
});
