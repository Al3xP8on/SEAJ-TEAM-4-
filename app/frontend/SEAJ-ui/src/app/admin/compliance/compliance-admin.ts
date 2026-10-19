import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, DestroyRef, ElementRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule, NgForm } from '@angular/forms';
import { finalize } from 'rxjs';
import {
  ComplianceAdminService,
  ComplianceDecision,
  ComplianceResult,
  ComplianceSubmission,
  RestrictedInstrument,
  SubmissionFilters,
} from './compliance-admin.service';

/** Plain names for rule ids, shown to admins */
export const RULE_NAMES: Record<string, string> = {
  RESTRICTED_LIST: 'Restricted list',
  CASH_AVAILABLE: 'Cash available',
  HOLDINGS_AVAILABLE: 'Holdings available',
  SINGLE_STOCK_LIMIT: 'Single-stock limit',
  RISK_PROFILE: 'Risk profile',
};

export const DECISION_LABELS: Record<ComplianceDecision, string> = {
  BLOCKED: 'Blocked',
  NEEDS_ACKNOWLEDGEMENT: 'Needs acknowledgement',
  ALLOWED: 'Allowed',
};

/**
 * Admin compliance page: who was stopped, by which rule and why (the audit trail), and the
 * restricted list customers can't buy from.
 */
@Component({
  selector: 'app-compliance-admin',
  imports: [CurrencyPipe, DatePipe, FormsModule],
  templateUrl: './compliance-admin.html',
  styleUrls: ['../admin.css', './compliance-admin.css'],
})
export class ComplianceAdminComponent {
  private readonly service = inject(ComplianceAdminService);
  private readonly destroy = inject(DestroyRef);
  private readonly host: ElementRef<HTMLElement> = inject(ElementRef);

  readonly ruleNames = RULE_NAMES;
  readonly decisionLabels = DECISION_LABELS;

  readonly submissions = signal<ComplianceSubmission[]>([]);
  readonly restricted = signal<RestrictedInstrument[]>([]);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  /** Symbol whose removal is waiting for a second click */
  readonly confirmingRemoval = signal<string | null>(null);

  filters: SubmissionFilters = { decision: '', accountId: '', symbol: '' };
  newSymbol = '';
  newReason = '';

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    this.service
      .restricted()
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (list) => this.restricted.set(list),
        error: () => this.error.set('Could not load the restricted list. Please retry.'),
      });
    this.loadSubmissions();
  }

  loadSubmissions(): void {
    this.loading.set(true);
    this.service
      .submissions(this.filters)
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroy),
      )
      .subscribe({
        next: (list) => this.submissions.set(list),
        error: () => this.error.set('Could not load compliance checks. Please retry.'),
      });
  }

  clearFilters(): void {
    this.filters = { decision: '', accountId: '', symbol: '' };
    this.loadSubmissions();
  }

  /** The rules that flagged something; passes are left out to keep rows short */
  flagged(submission: ComplianceSubmission): ComplianceResult[] {
    return submission.results.filter((result) => result.outcome !== 'PASS');
  }

  restrict(form: NgForm): void {
    if (this.saving()) return;
    if (!this.newSymbol.trim() || !this.newReason.trim()) {
      this.error.set('Enter a symbol and the reason customers will see.');
      return;
    }
    this.saving.set(true);
    this.error.set('');
    this.notice.set('');
    this.service
      .restrict(this.newSymbol, this.newReason)
      .pipe(
        finalize(() => this.saving.set(false)),
        takeUntilDestroyed(this.destroy),
      )
      .subscribe({
        next: (entry) => {
          this.restricted.update((list) =>
            [...list, entry].sort((a, b) => a.symbol.localeCompare(b.symbol)),
          );
          this.notice.set(
            `${entry.symbol} added to the restricted list. Customers can no longer buy it.`,
          );
          // Clear the fields directly: resetForm() would push null into the bound properties
          this.newSymbol = '';
          this.newReason = '';
          form.form.markAsPristine();
          form.form.markAsUntouched();
        },
        error: (error) =>
          this.error.set(error?.error?.message ?? 'Could not add the restriction. Please retry.'),
      });
  }

  remove(symbol: string): void {
    if (this.confirmingRemoval() !== symbol) {
      this.confirmingRemoval.set(symbol);
      this.focus(`#confirm-remove-${symbol}`);
      return;
    }
    this.saving.set(true);
    this.error.set('');
    this.notice.set('');
    this.service
      .unrestrict(symbol)
      .pipe(
        finalize(() => this.saving.set(false)),
        takeUntilDestroyed(this.destroy),
      )
      .subscribe({
        next: () => {
          this.restricted.update((list) => list.filter((entry) => entry.symbol !== symbol));
          this.confirmingRemoval.set(null);
          this.notice.set(`${symbol} removed from the restricted list.`);
          this.focus('#restrict-symbol');
        },
        error: (error) =>
          this.error.set(error?.error?.message ?? `Could not remove ${symbol}. Please retry.`),
      });
  }

  cancelRemoval(symbol: string): void {
    this.confirmingRemoval.set(null);
    this.focus(`#remove-${symbol}`);
  }

  private focus(selector: string): void {
    setTimeout(() => this.host.nativeElement.querySelector<HTMLElement>(selector)?.focus());
  }
}
