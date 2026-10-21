import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import {
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  ViewChild,
  afterNextRender,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule, NgForm } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subscription, finalize } from 'rxjs';
import { AccountRole, AuthService } from '../auth/auth.service';
import { AdminTabsComponent } from './admin-tabs/admin-tabs';
import { AdminObservabilityComponent } from './observability';
import { accountIdFromToken } from '../dashboard/dashboard.service';
import {
  AccountDetails,
  AccountStatus,
  AccountStatusHistory,
  AdminAccount,
  AdminService,
} from './admin.service';

@Component({
  selector: 'app-admin',
  imports: [AdminTabsComponent, 
    CurrencyPipe,
    DatePipe,
    DecimalPipe,
    FormsModule,
    RouterLink,
    AdminObservabilityComponent,
  ],
  templateUrl: './admin.html',
  styleUrls: ['./admin.css', './admin-overview.css'],
})
export class AdminComponent {
  private readonly service = inject(AdminService);
  private readonly auth = inject(AuthService);
  private readonly destroy = inject(DestroyRef);
  private readonly injector = inject(Injector);
  @ViewChild('confirmation') private confirmation!: ElementRef<HTMLDialogElement>;
  private detailRequest?: Subscription;
  private historyRequest?: Subscription;
  readonly activeSection = signal<'overview' | 'accounts' | 'observability'>('overview');
  readonly lastLoaded = signal<string | null>(null);
  readonly historyError = signal('');
  readonly closedCount = computed(
    () => this.accounts().filter((account) => account.status === 'CLOSED').length,
  );
  readonly recentAccounts = computed(() =>
    [...this.accounts()].sort((a, b) => b.lastUpdated.localeCompare(a.lastUpdated)).slice(0, 5),
  );
  readonly suspendedAccounts = computed(() =>
    this.accounts()
      .filter((account) => account.status === 'SUSPENDED')
      .slice(0, 4),
  );
  readonly activePercent = computed(() =>
    this.accounts().length ? (this.activeCount() / this.accounts().length) * 100 : 0,
  );
  readonly suspendedPercent = computed(() =>
    this.accounts().length ? (this.suspendedCount() / this.accounts().length) * 100 : 0,
  );

  showAccounts(status = 'ALL'): void {
    this.status.set(status);
    this.search.set('');
    this.activeSection.set('accounts');
  }

  openAccount(account: AdminAccount): void {
    this.activeSection.set('accounts');
    this.select(account);
  }

  startCreate(): void {
    this.activeSection.set('accounts');
    this.showCreate.set(true);
  }
  readonly ownId = accountIdFromToken(this.auth.getToken());
  readonly accounts = signal<AdminAccount[]>([]);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  readonly search = signal('');
  readonly status = signal('ALL');
  readonly selectedId = signal<number | null>(null);
  readonly selected = computed(
    () => this.accounts().find((account) => account.id === this.selectedId()) ?? null,
  );
  readonly details = signal<AccountDetails | null>(null);
  readonly detailsLoading = signal(false);
  readonly detailsError = signal('');
  readonly statusHistory = signal<AccountStatusHistory[]>([]);
  readonly historyLoading = signal(false);
  readonly visibleAccounts = computed(() => {
    const query = this.search().trim().toLowerCase();
    return this.accounts()
      .filter((account) => this.status() === 'ALL' || account.status === this.status())
      .filter((account) =>
        `${account.holderName} ${account.username} ${account.email ?? ''} ${account.accountId}`
          .toLowerCase()
          .includes(query),
      )
      .sort((a, b) => a.holderName.localeCompare(b.holderName));
  });
  readonly activeCount = computed(
    () => this.accounts().filter((account) => account.status === 'ACTIVE').length,
  );
  readonly suspendedCount = computed(
    () => this.accounts().filter((account) => account.status === 'SUSPENDED').length,
  );
  readonly totalCash = computed(() =>
    this.accounts().reduce((total, account) => total + account.cashBalance, 0),
  );
  readonly holdings = computed(
    () =>
      this.details()?.positions.filter(
        (position) =>
          position.quantity > 0 && ['OPEN', 'PARTIALLY_CLOSED'].includes(position.status),
      ) ?? [],
  );
  readonly holdingsValue = computed(() =>
    this.holdings().reduce(
      (total, position) => total + position.quantity * position.averageCost,
      0,
    ),
  );
  readonly accountOrders = computed(() =>
    [...(this.details()?.orders ?? [])].sort((a, b) => b.createdAt.localeCompare(a.createdAt)),
  );
  readonly pendingCount = computed(
    () => this.accountOrders().filter((order) => ['NEW', 'PENDING'].includes(order.status)).length,
  );
  readonly pendingAction = signal<{ account: AdminAccount; status: AccountStatus } | null>(null);
  reason = '';
  readonly saving = signal(false);
  readonly actionError = signal('');
  readonly showCreate = signal(false);
  readonly creating = signal(false);
  readonly createError = signal('');
  readonly challengeId = signal('');
  credentials = { name: '', username: '', email: '', password: '' };
  role: AccountRole = 'USER';
  code = '';

  constructor() {
    this.load();
  }

  load(): void {
    if (this.loading()) return;
    this.loading.set(true);
    this.error.set('');
    this.service
      .accounts()
      .pipe(
        takeUntilDestroyed(this.destroy),
        finalize(() => this.loading.set(false)),
      )
      .subscribe({
        next: (accounts) => {
          this.accounts.set(accounts);
          this.lastLoaded.set(new Date().toISOString());
          const selected = this.selected();
          if (selected) this.select(selected);
          else this.selectedId.set(null);
        },
        error: (error) =>
          this.error.set(message(error, 'Accounts could not be loaded. Please try again.')),
      });
  }

  select(account: AdminAccount): void {
    this.detailRequest?.unsubscribe();
    this.selectedId.set(account.id);
    this.details.set(null);
    this.statusHistory.set([]);
    this.detailsError.set('');
    this.detailsLoading.set(true);
    this.detailRequest = this.service
      .details(account.accountId)
      .pipe(
        takeUntilDestroyed(this.destroy),
        finalize(() => this.detailsLoading.set(false)),
      )
      .subscribe({
        next: (details) => this.details.set(details),
        error: (error) =>
          this.detailsError.set(message(error, 'Account details could not be loaded.')),
      });
    this.loadHistory(account.id);
  }

  loadHistory(id: number): void {
    this.historyRequest?.unsubscribe();
    this.historyError.set('');
    this.historyLoading.set(true);
    this.historyRequest = this.service
      .history(id)
      .pipe(
        takeUntilDestroyed(this.destroy),
        finalize(() => this.historyLoading.set(false)),
      )
      .subscribe({
        next: (history) => {
          if (this.selectedId() === id) this.statusHistory.set(history);
        },
        error: () => {
          if (this.selectedId() === id)
            this.historyError.set('Account history could not be loaded.');
        },
      });
  }

  askStatus(account: AdminAccount, status: AccountStatus): void {
    this.pendingAction.set({ account, status });
    this.reason = '';
    this.actionError.set('');
    // Render the content first so the browser can focus Cancel and trap focus.
    afterNextRender(
      () => {
        if (this.pendingAction()) this.confirmation.nativeElement.showModal();
      },
      { injector: this.injector },
    );
  }

  closeConfirmation(event?: Event): void {
    if (this.saving()) {
      event?.preventDefault();
      return;
    }
    this.confirmation.nativeElement.close();
    this.pendingAction.set(null);
  }

  saveStatus(): void {
    const action = this.pendingAction();
    if (!action || this.saving()) return;
    const reason = this.reason.trim();
    if ((action.status === 'SUSPENDED' || action.status === 'CLOSED') && !reason) {
      this.actionError.set('Enter a reason before continuing.');
      return;
    }
    this.saving.set(true);
    this.actionError.set('');
    this.notice.set('');
    this.service
      .changeStatus(action.account.id, action.status, reason)
      .pipe(
        takeUntilDestroyed(this.destroy),
        finalize(() => this.saving.set(false)),
      )
      .subscribe({
        next: (updated) => {
          this.accounts.update((accounts) =>
            accounts.map((account) => (account.id === updated.id ? updated : account)),
          );
          this.notice.set(
            `${updated.holderName}'s account is now ${updated.status.toLowerCase()}.`,
          );
          this.confirmation.nativeElement.close();
          this.pendingAction.set(null);
          if (this.selectedId() === updated.id) this.loadHistory(updated.id);
        },
        error: (error) =>
          this.actionError.set(message(error, 'The account status could not be changed.')),
      });
  }

  createAccount(form: NgForm): void {
    if (form.invalid || this.creating()) return;
    this.creating.set(true);
    this.createError.set('');
    const payload = {
      ...this.credentials,
      name: this.credentials.name.trim(),
      username: this.credentials.username.trim(),
      email: this.credentials.email.trim(),
    };
    this.auth
      .registerAdmin(payload, this.role)
      .pipe(
        takeUntilDestroyed(this.destroy),
        finalize(() => this.creating.set(false)),
      )
      .subscribe({
        next: (challenge) => {
          this.challengeId.set(challenge.challenge_id);
          this.credentials.password = '';
        },
        error: (error) =>
          this.createError.set(message(error, 'Registration could not be started.')),
      });
  }

  verifyAccount(form: NgForm): void {
    if (form.invalid || this.creating()) return;
    this.creating.set(true);
    this.createError.set('');
    this.auth
      .verifyRegistration(this.challengeId(), this.code)
      .pipe(
        takeUntilDestroyed(this.destroy),
        finalize(() => this.creating.set(false)),
      )
      .subscribe({
        next: (user) => {
          this.notice.set(
            `Account ${user.account_id} created for ${user.username}. The starting balance is $0.00.`,
          );
          this.resetCreate();
          this.load();
        },
        error: (error) =>
          this.createError.set(message(error, 'The verification code could not be confirmed.')),
      });
  }

  resetCreate(): void {
    this.showCreate.set(false);
    this.challengeId.set('');
    this.createError.set('');
    this.credentials = { name: '', username: '', email: '', password: '' };
    this.role = 'USER';
    this.code = '';
  }
}

function message(error: any, fallback: string): string {
  const detail = error?.error?.message ?? error?.message;
  return Array.isArray(detail)
    ? detail.join(' ')
    : typeof detail === 'string' && detail
      ? detail
      : fallback;
}
