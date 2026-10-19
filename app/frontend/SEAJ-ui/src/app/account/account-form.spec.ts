// Covers the restyled form markup: card autofill hints, hints and errors linked to their inputs,
// the selected section tab and the destructive delete button.
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { AccountComponent } from './account';
import { AccountService } from './account.service';

describe('Account form markup', () => {
  const account = { id: 7, accountId: 'ACC-7', holderName: 'Alice', cashBalance: 0, status: 'ACTIVE' };
  const settings = { phone: null, bank: null, cards: [], cash_balance: '0.00', transfers: [] };

  async function render() {
    TestBed.configureTestingModule({
      providers: [
        {
          provide: AccountService,
          useValue: { load: () => of({ account, profile: { username: 'alice', email: 'alice@example.com', email_verified: true }, settings }) },
        },
        { provide: AuthService, useValue: { logout: vi.fn() } },
        { provide: Router, useValue: { navigate: vi.fn() } },
      ],
    });
    const fixture = TestBed.createComponent(AccountComponent);
    await fixture.whenStable();
    return { fixture, page: fixture.nativeElement as HTMLElement };
  }

  function describedBy(page: HTMLElement, selector: string): string[] {
    return (page.querySelector(selector)?.getAttribute('aria-describedby') ?? '').split(/\s+/);
  }

  it('marks the card fields for autofill and links their hint and error', async () => {
    const { fixture, page } = await render();
    fixture.componentInstance.addingCard.set(true);
    await fixture.whenStable();
    expect(page.querySelector('#card-holder')?.getAttribute('autocomplete')).toBe('cc-name');
    expect(page.querySelector('#card-number')?.getAttribute('autocomplete')).toBe('cc-number');
    expect(page.querySelector('#card-month')?.getAttribute('autocomplete')).toBe('cc-exp-month');
    expect(page.querySelector('#card-year')?.getAttribute('autocomplete')).toBe('cc-exp-year');
    expect(describedBy(page, '#card-number')).toEqual(expect.arrayContaining(['card-hint', 'card-settings-error']));
  });

  it('links the password hint and error to the new password', async () => {
    const { page } = await render();
    expect(describedBy(page, '#new-password')).toEqual(expect.arrayContaining(['password-hint', 'password-error']));
  });

  it('links the amount hint and error on the cash form', async () => {
    const { fixture, page } = await render();
    fixture.componentInstance.section.set('cash');
    await fixture.whenStable();
    expect(describedBy(page, '#amount')).toEqual(expect.arrayContaining(['amount-hint', 'cash-error']));
    expect(page.querySelector('#transfer-number')?.getAttribute('autocomplete')).toBe('cc-number');
  });

  it('marks only the open section tab as pressed', async () => {
    const { page } = await render();
    const pressed = page.querySelectorAll('.section-nav button[aria-pressed="true"]');
    expect(pressed).toHaveLength(1);
    expect(pressed[0].textContent).toContain('Account settings');
  });

  it('shows delete account as a destructive action', async () => {
    const { page } = await render();
    expect(page.querySelector('#delete-account-button')?.className).toContain('text-destructive');
  });
});
