import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { AccountComponent } from './account';
import { AccountService } from './account.service';

describe('My Account', () => {
  const account = { id: 7, accountId: 'ACC-7', holderName: 'Alice', cashBalance: 100, status: 'ACTIVE' };
  const settings = { phone: null, bank: { bank_name: 'Test Bank', holder_name: 'Alice', account_last4: '1234', routing_last4: '5678' }, cash_balance: '100.00', transfers: [] };
  let service: { load: ReturnType<typeof vi.fn>; cash: ReturnType<typeof vi.fn>; password: ReturnType<typeof vi.fn>; savePhone: ReturnType<typeof vi.fn>; changeEmail: ReturnType<typeof vi.fn>; verifyEmail: ReturnType<typeof vi.fn>; startDeletion: ReturnType<typeof vi.fn>; verifyDeletion: ReturnType<typeof vi.fn>; confirmDeletion: ReturnType<typeof vi.fn>; removeCard: ReturnType<typeof vi.fn> };
  const auth = { logout: vi.fn() };
  const router = { navigate: vi.fn() };
  let component: AccountComponent;
  beforeEach(() => {
    service = { load: vi.fn(() => of({ account, profile: { username: 'alice', email: 'alice@example.com', email_verified: true }, settings })), cash: vi.fn(() => of({ ...settings, cash_balance: '125.00' })), password: vi.fn(() => of({})), savePhone: vi.fn(() => of({ ...settings, phone: '+1234567890' })), changeEmail: vi.fn(() => of({ challenge_id: 'challenge' })), verifyEmail: vi.fn(() => of({ email: 'new@example.com' })), startDeletion: vi.fn(() => of({ challenge_id: 'deletion-challenge' })), verifyDeletion: vi.fn(() => of({ deletion_token: 'approval' })), confirmDeletion: vi.fn(() => of({})), removeCard: vi.fn(() => of({ ...settings, bank: null, cards: [] })) };
    TestBed.configureTestingModule({ providers: [{ provide: AccountService, useValue: service }, { provide: AuthService, useValue: auth }, { provide: Router, useValue: router }] });
    component = TestBed.createComponent(AccountComponent).componentInstance;
  });
  it('blocks invalid precision, oversized transfers and insufficient funds', () => {
    for (const amount of ['0', '-1', '1.001', '1000001', 'abc']) {
      component.amount = amount; component.reviewCash(); expect(component.review()).toBeNull(); expect(component.cashError()).toBeTruthy();
    }
    component.action = 'withdraw'; component.amount = '101'; component.reviewCash();
    expect(component.review()).toBeNull(); expect(service.cash).not.toHaveBeenCalled();
  });
  it('requires review and confirmation before updating the returned balance', () => {
    component.amount = '25'; component.reviewCash(); expect(service.cash).not.toHaveBeenCalled();
    component.submitCash(); expect(service.cash).toHaveBeenCalledWith('deposit', '25.00', expect.any(String), undefined, undefined, false); expect(component.account()?.cashBalance).toBe(125); expect(component.review()).toBeNull();
  });
  it('rejects mismatched and multibyte oversized passwords, then clears successful credentials', () => {
    component.currentPassword = 'old-password'; component.newPassword = 'new-password'; component.confirmPassword = 'different'; component.submitPassword();
    expect(service.password).not.toHaveBeenCalled();
    component.newPassword = '😀'.repeat(20); component.confirmPassword = component.newPassword; component.submitPassword(); expect(service.password).not.toHaveBeenCalled();
    component.newPassword = component.confirmPassword = 'new-password'; component.submitPassword(); expect(service.password).toHaveBeenCalledWith('old-password', 'new-password'); expect(component.currentPassword).toBe('');
  });
  it('allows a one-off transfer without saving card details', () => {
    component.settings.set({ ...settings, bank: null }); component.useSavedCard = false;
    component.transferCard = { holder_name: 'Alice', card_number: '4111111111111111', expiry_month: 12, expiry_year: 2099 };
    component.amount = '25'; component.reviewCash(); component.submitCash();
    expect(service.cash).toHaveBeenCalledWith('deposit', '25.00', expect.any(String), expect.objectContaining({ card_number: '4111111111111111' }), undefined, false);
    expect(component.transferCard.card_number).toBe('');
  });
  it('prompts to save an additional card and sends the choice with the confirmed transfer', () => {
    component.useSavedCard = false; component.transferCard = { holder_name: 'Alice', card_number: '4111111111111111', expiry_month: 12, expiry_year: 2099 };
    component.amount = '25'; component.reviewCash(); expect(service.cash).not.toHaveBeenCalled();
    component.saveTransferCard = true; component.submitCash();
    expect(service.cash).toHaveBeenCalledWith('deposit', '25.00', expect.any(String), expect.objectContaining({ card_number: '4111111111111111' }), undefined, true);
  });
  it('allows a deposit above the former demo payment balance', () => {
    component.amount = '20000'; component.reviewCash(); expect(component.cashError()).toBe(''); expect(component.review()?.amount).toBe('20000.00');
  });
  it('shows immutable identifiers outside the edit form and hides card fields until requested', () => {
    const fixture = TestBed.createComponent(AccountComponent); fixture.detectChanges();
    const root: HTMLElement = fixture.nativeElement;
    expect(root.querySelector('#card-number')).toBeNull(); expect(root.querySelector('#phone')).toBeNull();
    fixture.componentInstance.editAccount(); fixture.detectChanges();
    expect(root.querySelector('#phone')).not.toBeNull(); expect(root.querySelector('input[name="username"]')).toBeNull(); expect(root.querySelector('input[name="accountId"]')).toBeNull();
    fixture.componentInstance.addingCard.set(true); fixture.detectChanges(); expect(root.querySelector('#card-number')).not.toBeNull();
  });
  it('keeps the current email until the new email is verified', () => {
    component.editAccount(); component.phone = '+1234567890'; component.email = 'new@example.com'; component.saveAccount();
    expect(component.settings()?.phone).toBe('+1234567890'); expect(component.profile()?.email).toBe('alice@example.com'); expect(component.emailChallenge()).toBe('challenge');
    component.emailCode = '123456'; component.verifyEmail(); expect(component.profile()?.email).toBe('new@example.com'); expect(component.editing()).toBe(false);
  });
  it('does not request an email change when saving account information fails', () => {
    service.savePhone.mockReturnValue(throwError(() => new Error('offline')));
    component.email = 'new@example.com'; component.saveAccount(); expect(service.changeEmail).not.toHaveBeenCalled(); expect(component.settingsError()).toBeTruthy();
  });
  it('blocks deletion until cash is withdrawn and only shows confirmation after verification', () => {
    component.startDeletion(); expect(service.startDeletion).not.toHaveBeenCalled(); expect(component.withdrawalRequired()).toBe(true);
    component.account.set({ ...component.account()!, cashBalance: 0 }); component.startDeletion();
    expect(component.deletionToken()).toBeNull(); expect(service.confirmDeletion).not.toHaveBeenCalled();
    component.deletionCode = '123456'; component.verifyDeletion(); expect(component.deletionToken()).toBe('approval');
    component.confirmDeletion(); expect(service.confirmDeletion).toHaveBeenCalledWith('approval'); expect(auth.logout).toHaveBeenCalled(); expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });
  it('keeps deletion clickable with cash and offers a withdrawal shortcut after clicking', () => {
    const fixture = TestBed.createComponent(AccountComponent); fixture.detectChanges();
    const root: HTMLElement = fixture.nativeElement;
    const deleteButton = root.querySelector<HTMLButtonElement>('#delete-account-button')!;
    expect(deleteButton.disabled).toBe(false); expect(root.querySelector('#withdraw-remaining-button')).toBeNull();
    deleteButton.click(); fixture.detectChanges();
    expect(root.textContent).toContain("Don't forget to withdraw your cash before deleting your account");
    root.querySelector<HTMLButtonElement>('#withdraw-remaining-button')!.click(); fixture.detectChanges();
    expect(fixture.componentInstance.section()).toBe('cash'); expect(fixture.componentInstance.action).toBe('withdraw'); expect(fixture.componentInstance.amount).toBe('100.00'); expect(service.cash).not.toHaveBeenCalled();
  });
  it('removes only the selected card and refreshes the saved-card list', () => {
    component.settings.set({ ...settings, cards: [{ ...settings.bank, id: 'card-1' }] }); component.paymentChoice = 'card-1';
    component.removeCard('card-1'); expect(service.removeCard).toHaveBeenCalledWith('card-1'); expect(component.savedCards()).toHaveLength(0); expect(component.paymentChoice).toBe('new');
  });
  it('retains the existing balance when a cash request fails', () => {
    service.cash.mockReturnValue(throwError(() => new Error('offline')));
    component.amount = '25'; component.reviewCash(); component.submitCash();
    expect(component.account()?.cashBalance).toBe(100); expect(component.cashBusy()).toBe(false); expect(component.cashError()).toBeTruthy();
  });
});
