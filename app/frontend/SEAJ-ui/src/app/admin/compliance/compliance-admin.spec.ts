// Covers the admin compliance page: the audit trail with its filters and reasons, and adding and
// removing restricted instruments, including the two-step removal and where focus goes.
import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ComplianceAdminComponent } from './compliance-admin';
import {
  ComplianceAdminService,
  ComplianceSubmission,
  RestrictedInstrument,
} from './compliance-admin.service';

const blocked: ComplianceSubmission = {
  checkGroupId: 'g-1',
  checkedAt: '2026-10-19T12:00:00',
  accountId: 'ACC-0002',
  symbol: 'TSLA',
  side: 'BUY',
  quantity: 1,
  price: 200,
  decision: 'BLOCKED',
  orderId: null,
  results: [
    {
      rule: 'RESTRICTED_LIST',
      outcome: 'BLOCK',
      reason: "You can't buy TSLA. It's on the restricted list: review",
      acknowledged: false,
    },
    { rule: 'CASH_AVAILABLE', outcome: 'PASS', reason: null, acknowledged: false },
  ],
};
const acknowledged: ComplianceSubmission = {
  ...blocked,
  checkGroupId: 'g-2',
  symbol: 'NVDA',
  quantity: 30,
  price: 100,
  decision: 'ALLOWED',
  orderId: 'order-9',
  results: [
    {
      rule: 'SINGLE_STOCK_LIMIT',
      outcome: 'WARN',
      reason: 'NVDA would be 30% of your portfolio.',
      acknowledged: true,
    },
  ],
};
const clean: ComplianceSubmission = {
  ...blocked,
  checkGroupId: 'g-3',
  symbol: 'AAPL',
  decision: 'ALLOWED',
  orderId: 'order-10',
  results: [{ rule: 'RESTRICTED_LIST', outcome: 'PASS', reason: null, acknowledged: false }],
};
const tsla: RestrictedInstrument = {
  symbol: 'TSLA',
  name: 'Tesla Inc.',
  reason: 'under internal compliance review',
  addedBy: 'system',
  addedOn: '2026-10-17T09:00:00',
};

describe('Compliance admin page', () => {
  const service = {
    submissions: vi.fn(),
    restricted: vi.fn(),
    restrict: vi.fn(),
    unrestrict: vi.fn(),
    riskProfiles: vi.fn(),
    setRiskProfile: vi.fn(),
  };
  const brian = {
    id: 2,
    accountId: 'ACC-0002',
    holderName: 'Brian Osei',
    username: 'brian',
    status: 'ACTIVE' as const,
    riskProfile: 'BALANCED' as const,
  };
  const carla = { ...brian, id: 3, accountId: 'ACC-0003', holderName: 'Carla Mendes', status: 'CLOSED' as const };

  beforeEach(async () => {
    Object.values(service).forEach((mock) => mock.mockReset());
    service.submissions.mockReturnValue(of([blocked, acknowledged, clean]));
    service.restricted.mockReturnValue(of([tsla]));
    service.riskProfiles.mockReturnValue(of([brian, carla]));
    await TestBed.configureTestingModule({
      imports: [ComplianceAdminComponent],
      providers: [provideRouter([]), { provide: ComplianceAdminService, useValue: service }],
    }).compileComponents();
  });

  async function render() {
    const fixture = TestBed.createComponent(ComplianceAdminComponent);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    document.body.appendChild(page);
    return { fixture, component: fixture.componentInstance, page };
  }

  afterEach(() =>
    document.body
      .querySelectorAll('app-compliance-admin, [ng-version]')
      .forEach((el) => el.remove()),
  );

  /** Types into an input the way a user would, so the form sees the change */
  function type(input: HTMLInputElement, value: string) {
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  const rows = (page: HTMLElement, label: string) =>
    Array.from(page.querySelectorAll(`[aria-label="${label}"] tbody tr`)).map((row) =>
      row.textContent!.replace(/\s+/g, ' ').trim(),
    );

  it('shows each submission with its decision in words and only the rules that flagged it', async () => {
    const { page } = await render();
    const [first, second, third] = rows(page, 'Compliance checks table');
    expect(first).toContain('Buy 1 TSLA');
    expect(first).toContain('Blocked');
    expect(first).toContain("Restricted list (block): You can't buy TSLA");
    expect(first).not.toContain('Cash available');
    expect(first).toMatch(/No$/);
    expect(second).toContain('Single-stock limit (warning, acknowledged): NVDA would be 30%');
    expect(second).toMatch(/Yes$/);
    expect(third).toContain('All checks passed');
  });

  it('applies and clears filters', async () => {
    const { fixture, component } = await render();
    component.filters = { decision: 'BLOCKED', accountId: 'ACC-0002', symbol: 'tsla' };
    component.loadSubmissions();
    expect(service.submissions).toHaveBeenLastCalledWith({
      decision: 'BLOCKED',
      accountId: 'ACC-0002',
      symbol: 'tsla',
    });
    component.clearFilters();
    await fixture.whenStable();
    expect(service.submissions).toHaveBeenLastCalledWith({
      decision: '',
      accountId: '',
      symbol: '',
    });
  });

  it('adds a restriction and keeps the list sorted', async () => {
    service.restrict.mockReturnValue(
      of({
        symbol: 'META',
        name: 'Meta Platforms Inc.',
        reason: 'pending corporate action',
        addedBy: 'jay',
        addedOn: '2026-10-19T13:00:00',
      }),
    );
    const { fixture, component, page } = await render();
    const symbol = page.querySelector<HTMLInputElement>('#restrict-symbol')!;
    const reason = page.querySelector<HTMLInputElement>('.restrict-form input[name="reason"]')!;
    type(symbol, 'meta');
    type(reason, 'pending corporate action');
    fixture.detectChanges(); // as the browser does after each keystroke
    page.querySelector<HTMLButtonElement>('.restrict-form button[type="submit"]')!.click();
    await fixture.whenStable();

    expect(service.restrict).toHaveBeenCalledWith('meta', 'pending corporate action');
    expect(component.restricted().map((entry) => entry.symbol)).toEqual(['META', 'TSLA']);
    expect(component.notice()).toContain('META added to the restricted list');
    // The form is ready for the next one (ngModel writes back to the inputs a tick later)
    fixture.detectChanges();
    await fixture.whenStable();
    expect(symbol.value).toBe('');
    expect(reason.value).toBe('');
  });

  it('shows the server reason when a restriction is refused', async () => {
    service.restrict.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { message: 'TSLA is already on the restricted list.' },
          }),
      ),
    );
    const { fixture, component, page } = await render();
    component.newSymbol = 'TSLA';
    component.newReason = 'again';
    page.querySelector<HTMLButtonElement>('.restrict-form button[type="submit"]')!.click();
    await fixture.whenStable();
    expect(component.error()).toBe('TSLA is already on the restricted list.');
  });

  it('needs a symbol and a reason before asking the server', async () => {
    const { fixture, component, page } = await render();
    component.newSymbol = 'META';
    page.querySelector<HTMLButtonElement>('.restrict-form button[type="submit"]')!.click();
    await fixture.whenStable();
    expect(service.restrict).not.toHaveBeenCalled();
    expect(component.error()).toBe('Enter a symbol and the reason customers will see.');
  });

  it('asks for confirmation before removing, and moves focus to the confirm button', async () => {
    service.unrestrict.mockReturnValue(of(undefined));
    const { fixture, component, page } = await render();
    page.querySelector<HTMLButtonElement>('#remove-TSLA')!.click();
    await fixture.whenStable();
    expect(service.unrestrict).not.toHaveBeenCalled();
    await new Promise((resolve) => setTimeout(resolve));
    expect(document.activeElement?.id).toBe('confirm-remove-TSLA');

    page.querySelector<HTMLButtonElement>('#confirm-remove-TSLA')!.click();
    await fixture.whenStable();
    expect(service.unrestrict).toHaveBeenCalledWith('TSLA');
    expect(component.restricted()).toEqual([]);
    expect(component.notice()).toBe('TSLA removed from the restricted list.');
  });

  it('cancelling a removal returns focus to the Remove button', async () => {
    const { fixture, page } = await render();
    page.querySelector<HTMLButtonElement>('#remove-TSLA')!.click();
    await fixture.whenStable();
    page.querySelector<HTMLButtonElement>('#confirm-remove-TSLA + button')!.click();
    await fixture.whenStable();
    await new Promise((resolve) => setTimeout(resolve));
    expect(document.activeElement?.id).toBe('remove-TSLA');
    expect(service.unrestrict).not.toHaveBeenCalled();
  });

  it('explains a failed load', async () => {
    service.submissions.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    const { component } = await render();
    expect(component.error()).toBe('Could not load compliance checks. Please retry.');
  });

  describe('risk profiles', () => {
    const select = (page: HTMLElement, name: string) =>
      page.querySelector<HTMLSelectElement>(`select[aria-label="Risk profile for ${name}"]`)!;
    const save = (page: HTMLElement, name: string) =>
      page.querySelector<HTMLButtonElement>(`button[aria-label="Save risk profile for ${name}"]`)!;

    it('lists each account with its profile and explains the limits', async () => {
      const { page } = await render();
      expect(rows(page, 'Risk profiles table')[0]).toContain('Brian Osei');
      expect(page.querySelector('.profile-limits')!.textContent).toContain('Blocks orders over $5,000');
      expect(select(page, 'Brian Osei').disabled).toBe(false);
      expect(select(page, 'Carla Mendes').disabled).toBe(true);
    });

    it('enables Save only once the profile changes, then saves it', async () => {
      service.setRiskProfile.mockReturnValue(of({ ...brian, riskProfile: 'CONSERVATIVE' }));
      const { fixture, component, page } = await render();
      expect(save(page, 'Brian Osei').disabled).toBe(true);

      component.choose(brian, 'CONSERVATIVE');
      await fixture.whenStable();
      expect(save(page, 'Brian Osei').disabled).toBe(false);

      save(page, 'Brian Osei').click();
      await fixture.whenStable();
      expect(service.setRiskProfile).toHaveBeenCalledWith(2, 'CONSERVATIVE');
      expect(component.accounts()[0].riskProfile).toBe('CONSERVATIVE');
      expect(component.drafts()).toEqual({});
      expect(component.notice()).toBe(
        "Brian Osei's risk profile is now Conservative. It applies from their next order.",
      );
    });

    it('choosing the saved profile again clears the pending change', async () => {
      const { component } = await render();
      component.choose(brian, 'AGGRESSIVE');
      component.choose(brian, 'BALANCED');
      expect(component.drafts()).toEqual({});
    });

    it('keeps the choice and explains when saving fails', async () => {
      service.setRiskProfile.mockReturnValue(
        throwError(
          () => new HttpErrorResponse({ status: 409, error: { message: "Closed accounts can't be changed." } }),
        ),
      );
      const { fixture, component } = await render();
      component.choose(brian, 'AGGRESSIVE');
      component.saveProfile(brian);
      await fixture.whenStable();
      expect(component.error()).toBe("Closed accounts can't be changed.");
      expect(component.chosen(brian)).toBe('AGGRESSIVE');
    });
  });
});
