import { HlmSwitch } from '@spartan-ng/helm/switch';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { Component, DestroyRef, ElementRef, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { AccountService, Profile, Settings, BankInput, Bank } from './account.service';
import { Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { AppearanceService } from '../appearance/appearance.service';
import { Account } from '../dashboard/dashboard.models';
@Component({ selector: 'app-account', imports: [FormsModule, CurrencyPipe, DatePipe, HlmSwitch, HlmCardImports], templateUrl: './account.html', styleUrl: './account.css' })
export class AccountComponent {
  private readonly element: ElementRef<HTMLElement> = inject(ElementRef);
  private readonly service = inject(AccountService);
  private readonly destroyRef = inject(DestroyRef);
  readonly account = signal<Account | null>(null);
  readonly profile = signal<Profile | null>(null);
  readonly appearance = inject(AppearanceService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  readonly deletionBusy = signal(false);
  readonly withdrawalRequired = signal(false);
  readonly deletionError = signal('');
  readonly deletionMessage = signal('');
  readonly deletionChallenge = signal<string | null>(null);
  readonly deletionToken = signal<string | null>(null);
  deletionCode = '';
  hasCash() { return Number(this.account()?.cashBalance ?? 0) !== 0; }
  readonly editing = signal(false);
  readonly addingCard = signal(false);
  email = '';
  emailCode = '';
  readonly emailChallenge = signal<string | null>(null);
  paymentChoice = 'new';
  saveTransferCard = false;
  savedCards(): Bank[] {
    const settings = this.settings();
    if (settings?.cards?.length) return settings.cards;
    return settings?.bank ? [settings.bank] : [];
  }
  get useSavedCard() { return this.paymentChoice !== 'new'; }
  set useSavedCard(value: boolean) { this.paymentChoice = value ? this.savedCards()[0]?.id ?? 'legacy' : 'new'; }
  private selectedCard() { return this.savedCards().find(card => (card.id ?? 'legacy') === this.paymentChoice); }
  transferCard: BankInput = { holder_name: '', card_number: '', expiry_month: 1, expiry_year: new Date().getUTCFullYear() + 1 };
  readonly section = signal<'settings' | 'cash' | 'appearance'>('settings');
  readonly settings = signal<Settings | null>(null);
  readonly settingsBusy = signal(false);
  readonly cardError = signal('');
  readonly cardMessage = signal('');
  readonly settingsError = signal('');
  readonly settingsMessage = signal('');
  phone = '';
  bankInput: BankInput = { holder_name: '', card_number: '', expiry_month: 1, expiry_year: new Date().getUTCFullYear() + 1 };
  readonly loading = signal(false);
  readonly loadError = signal('');
  readonly cashBusy = signal(false);
  readonly passwordBusy = signal(false);
  readonly cashError = signal('');
  readonly passwordError = signal('');
  readonly cashMessage = signal('');
  readonly passwordMessage = signal('');
  readonly review = signal<{ action: 'deposit' | 'withdraw'; amount: string; requestId: string; card?: BankInput; last4: string; cardId?: string } | null>(null);
  action: 'deposit' | 'withdraw' = 'deposit';
  amount = '';
  currentPassword = '';
  newPassword = '';
  confirmPassword = '';
  constructor() { this.load(); }
  load() {
    this.loading.set(true); this.loadError.set('');
    this.service.load().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: data => { this.account.set(data.account); this.profile.set(data.profile); this.applySettings(data.settings); this.phone = data.settings.phone ?? ''; this.email = data.profile.email; this.paymentChoice = data.settings.cards?.[0]?.id ?? (data.settings.bank ? 'legacy' : 'new'); this.bankInput.holder_name = data.settings.bank?.holder_name ?? ''; this.loading.set(false); },
      error: () => { this.loadError.set('Unable to load your account. Please try again.'); this.loading.set(false); },
    });
  }
  private applySettings(settings: Settings) {
    this.settings.set(settings);
    const account = this.account();
    if (account) this.account.set({ ...account, cashBalance: Number(settings.cash_balance) });
  }
  private focus(selector: string) { setTimeout(() => this.element.nativeElement.querySelector<HTMLElement>(selector)?.focus()); }
  toggleCard() { this.cardError.set(''); this.cardMessage.set(''); this.addingCard.update(open => !open); this.focus(this.addingCard() ? '#card-holder' : '#edit-card-button'); }
  editAccount() { this.focus('#email'); this.phone = this.settings()?.phone ?? ''; this.email = this.profile()?.email ?? ''; this.editing.set(true); this.settingsError.set(''); this.settingsMessage.set(''); }
  cancelEdit() { this.focus('#edit-account-button'); this.editing.set(false); this.emailChallenge.set(null); this.emailCode = ''; this.settingsError.set(''); }
  saveAccount() {
    if (this.settingsBusy()) return;
    this.settingsError.set(''); this.settingsMessage.set('');
    if (this.phone && !/^(?=.{7,20}$)\+?[0-9 ()-]+$/.test(this.phone)) {
      this.settingsError.set('Enter a valid phone number with 7–20 characters.'); this.focus('#phone'); return;
    }
    this.settingsBusy.set(true);
    this.service.savePhone(this.phone).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: settings => {
        this.applySettings(settings);
        if (this.email === this.profile()?.email) {
          this.settingsBusy.set(false); this.editing.set(false); this.focus('#edit-account-button'); this.settingsMessage.set('Account information saved.'); return;
        }
        this.service.changeEmail(this.email).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
          next: challenge => { this.settingsBusy.set(false); this.emailChallenge.set(challenge.challenge_id); this.focus('#email-code'); this.settingsMessage.set('Phone number saved. Enter the code sent to your new email. Your current email stays active until verified.'); },
          error: error => { this.settingsBusy.set(false); this.settingsError.set(this.errorMessage(error, 'Phone number saved, but email verification could not be sent. Try again.')); },
        });
      },
      error: error => { this.settingsBusy.set(false); this.settingsError.set(this.errorMessage(error, 'Unable to save account information.')); },
    });
  }
  verifyEmail() {
    const challenge = this.emailChallenge(); if (!challenge || this.settingsBusy()) return;
    this.settingsBusy.set(true);
    this.service.verifyEmail(challenge, this.emailCode).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: result => {
        const profile = this.profile();
        if (profile) {
          this.profile.set({ ...profile, email: result.email });
        }
        this.settingsBusy.set(false); this.emailChallenge.set(null); this.editing.set(false); this.focus('#edit-account-button'); this.emailCode = ''; this.settingsMessage.set('Account information saved.');
      },
      error: error => { this.settingsBusy.set(false); this.settingsError.set(this.errorMessage(error, 'Unable to verify your new email.')); },
    });
  }
  private validCard(card: BankInput): boolean {
    const now = new Date();
    return !!card.holder_name.trim() && /^\d{13,19}$/.test(card.card_number) && Number(card.expiry_month) >= 1 && Number(card.expiry_month) <= 12 && (Number(card.expiry_year) > now.getUTCFullYear() || (Number(card.expiry_year) === now.getUTCFullYear() && Number(card.expiry_month) >= now.getUTCMonth() + 1));
  }
  saveBank() {
    if (this.settingsBusy() || this.cashBusy()) return;
    this.cardError.set(''); this.cardMessage.set('');
    if (!this.validCard(this.bankInput)) {
      this.cardError.set('Enter a cardholder name, a 13–19 digit card number and a future expiry date.'); return;
    }
    this.settingsBusy.set(true); this.review.set(null);
    this.service.saveBank({ ...this.bankInput, holder_name: this.bankInput.holder_name.trim() }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: settings => { this.applySettings(settings); this.bankInput.card_number = ''; this.addingCard.set(false); this.focus('#edit-card-button'); this.paymentChoice = settings.bank?.id ?? 'legacy'; this.settingsBusy.set(false); this.cardMessage.set('Card details saved.'); },
      error: error => { this.settingsBusy.set(false); this.cardError.set(this.errorMessage(error, 'Unable to save card details.')); },
    });
  }
  removeCard(cardId: string | undefined) {
    if (!cardId || this.settingsBusy() || this.cashBusy()) return;
    this.settingsBusy.set(true); this.cardError.set(''); this.cardMessage.set(''); this.review.set(null);
    this.service.removeCard(cardId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: settings => {
        this.applySettings(settings);
        if (!this.selectedCard()) {
          this.paymentChoice = settings.cards?.[0]?.id ?? 'new';
        }
        this.settingsBusy.set(false); this.cardMessage.set('Card removed.'); this.focus('#edit-card-button');
      },
      error: error => { this.settingsBusy.set(false); this.cardError.set(this.errorMessage(error, 'Unable to remove card.')); },
    });
  }
  cancelDeletion() {
    this.withdrawalRequired.set(false);
    this.deletionChallenge.set(null); this.deletionToken.set(null); this.deletionCode = ''; this.deletionError.set(''); this.deletionMessage.set(''); this.focus('#delete-account-button');
  }
  startDeletion() {
    if (this.deletionBusy()) return;
    this.deletionError.set(''); this.deletionMessage.set('');
    if (this.hasCash()) { this.withdrawalRequired.set(true); this.focus('#withdraw-remaining-button'); return; }
    this.withdrawalRequired.set(false);
    this.deletionBusy.set(true);
    this.service.startDeletion().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: challenge => { this.deletionBusy.set(false); this.deletionToken.set(null); this.deletionCode = ''; this.deletionChallenge.set(challenge.challenge_id); this.deletionMessage.set('A verification code has been sent to your account email.'); this.focus('#deletion-code'); },
      error: error => { this.deletionBusy.set(false); this.deletionError.set(this.deletionFailure(error, 'Unable to send account deletion code.')); },
    });
  }
  withdrawRemainingCash() {
    this.section.set('cash'); this.action = 'withdraw';
    this.amount = Number(this.account()?.cashBalance ?? 0).toFixed(2);
    this.review.set(null); this.cashError.set(''); this.cashMessage.set('');
    this.deletionChallenge.set(null); this.deletionToken.set(null); this.deletionCode = '';
    this.withdrawalRequired.set(false); this.focus('#amount');
  }
  verifyDeletion() {
    const challenge = this.deletionChallenge(); if (!challenge || this.deletionBusy()) return;
    this.deletionBusy.set(true); this.deletionError.set('');
    this.service.verifyDeletion(challenge, this.deletionCode).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: result => { this.deletionBusy.set(false); this.deletionToken.set(result.deletion_token); this.deletionCode = ''; this.deletionMessage.set('Email verified. Confirm deletion within five minutes.'); this.focus('#confirm-delete-button'); },
      error: error => { this.deletionBusy.set(false); this.deletionError.set(this.deletionFailure(error, 'Unable to verify deletion code.')); },
    });
  }
  confirmDeletion() {
    const token = this.deletionToken(); if (!token || this.deletionBusy()) return;
    this.deletionBusy.set(true); this.deletionError.set('');
    this.service.confirmDeletion(token).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => { this.auth.logout(); this.router.navigate(['/login']); },
      error: error => { this.deletionBusy.set(false); this.deletionToken.set(null); this.deletionChallenge.set(null); this.deletionError.set(this.deletionFailure(error, 'Unable to delete account. Please request a new code.')); },
    });
  }
  private deletionFailure(error: unknown, fallback: string) {
    return error instanceof HttpErrorResponse && typeof error.error?.message === 'string' ? error.error.message : fallback;
  }
  reviewCash() {
    this.cashError.set(''); this.cashMessage.set('');
    const saved = this.selectedCard();
    const card = saved ? undefined : { ...this.transferCard };
    if (card && !this.validCard(card)) { this.cashError.set('Enter a cardholder name, a 13–19 digit card number and a future expiry date, or choose your saved card.'); return; }
    if (!/^\d+(\.\d{1,2})?$/.test(this.amount) || Number(this.amount) <= 0 || Number(this.amount) > 1000000) {
      this.cashError.set('Enter an amount from 0.01 to 1,000,000 with at most two decimal places.'); return;
    }
    if (this.action === 'withdraw' && Number(this.amount) > Number(this.account()?.cashBalance)) {
      this.cashError.set('The withdrawal amount exceeds your cash balance.'); return;
    }
    this.review.set({ action: this.action, amount: Number(this.amount).toFixed(2), requestId: this.review()?.requestId ?? this.transferId(), card, last4: card?.card_number.slice(-4) ?? saved?.account_last4 ?? '', cardId: saved?.id });
  }
  private transferId(): string {
    const bytes = crypto.getRandomValues(new Uint8Array(16));
    bytes[6] = (bytes[6] & 15) | 64; bytes[8] = (bytes[8] & 63) | 128;
    const hex = Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('');
    return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
  }
  submitCash() {
    const review = this.review(); if (!review || this.cashBusy()) return;
    this.cashBusy.set(true); this.cashError.set('');
    this.service.cash(review.action, review.amount, review.requestId, review.card, review.cardId, !!review.card && this.saveTransferCard).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: settings => {
        this.applySettings(settings);
        if (review.card && this.saveTransferCard) {
          this.paymentChoice = settings.bank?.id ?? 'legacy';
        }
        this.saveTransferCard = false; this.cashBusy.set(false); this.review.set(null); this.amount = ''; this.transferCard.card_number = ''; this.focus('#amount'); this.cashMessage.set('Transfer completed. Your cash balance has been updated.');
      },
      error: error => { this.cashBusy.set(false); this.cashError.set(this.errorMessage(error, 'Cash transfer failed. Check your balance before trying again.')); },
    });
  }
  submitPassword() {
    if (this.passwordBusy()) return;
    this.passwordError.set(''); this.passwordMessage.set('');
    if (this.newPassword.length < 8 || new TextEncoder().encode(this.newPassword).length > 72) {
      this.passwordError.set('New password must be at least 8 characters and at most 72 bytes.'); return;
    }
    if (this.newPassword !== this.confirmPassword) { this.passwordError.set('The new passwords do not match.'); return; }
    this.passwordBusy.set(true);
    this.service.password(this.currentPassword, this.newPassword).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => { this.currentPassword = ''; this.newPassword = ''; this.confirmPassword = ''; this.passwordBusy.set(false); this.passwordMessage.set('Password changed successfully.'); },
      error: error => { this.passwordBusy.set(false); this.passwordError.set(this.errorMessage(error, 'Unable to change your password. Please try again.')); },
    });
  }
  private errorMessage(error: unknown, fallback: string): string {
    if (error instanceof HttpErrorResponse && error.status === 401) return 'Your session expired or your current password is incorrect. Sign in again or check your password.';
    if (error instanceof HttpErrorResponse && [400, 409].includes(error.status) && typeof error.error?.message === 'string') return error.error.message.replace(/simulated\s*/gi, '');
    if (error instanceof HttpErrorResponse && error.status === 400) return 'Check your entries and current balance.';
    return fallback;
  }
}
