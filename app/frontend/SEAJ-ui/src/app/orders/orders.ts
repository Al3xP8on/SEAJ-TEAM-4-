import {
  Component,
  DestroyRef,
  ElementRef,
  HostListener,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule, NgForm } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, catchError, exhaustMap, finalize, forkJoin, timer } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { Account, Position } from '../dashboard/dashboard.models';
import { accountIdFromToken } from '../dashboard/dashboard.service';
import {
  Instrument,
  Order,
  OrderHistory,
  OrderRequest,
  OrderStatus,
  Quote,
  TradingAccount,
} from './orders.models';
import { OrdersService } from './orders.service';

@Component({
  selector: 'app-orders',
  imports: [FormsModule, CurrencyPipe, DecimalPipe, DatePipe],
  templateUrl: './orders.html',
  styleUrl: './orders.css',
})
export class OrdersComponent {
  private readonly service = inject(OrdersService);
  private readonly auth = inject(AuthService);
  private readonly host: ElementRef<HTMLElement> = inject(ElementRef);
  private previousFocus: HTMLElement | null = null;
  private readonly destroy = inject(DestroyRef);
  readonly accounts = signal<TradingAccount[]>([]);
  readonly instruments = signal<Instrument[]>([]);
  readonly account = signal<Account | null>(null);
  readonly positions = signal<Position[]>([]);
  readonly orders = signal<Order[]>([]);
  readonly quote = signal<Quote | null>(null);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly refreshError = signal('');
  readonly notice = signal('');
  readonly submitting = signal(false);
  readonly actionId = signal('');
  readonly confirming = signal<Order | null>(null);
  readonly historyOrder = signal<Order | null>(null);
  readonly history = signal<OrderHistory[]>([]);
  readonly historyLoading = signal(false);
  readonly historyError = signal('');
  readonly accountFilter = signal('');
  readonly statusFilter = signal('ALL');
  readonly search = signal('');
  readonly isAdmin = this.readAdmin();
  accountId = '';
  symbol = '';
  side: 'BUY' | 'SELL' = 'BUY';
  orderType: 'MARKET' | 'LIMIT' = 'MARKET';
  quantity: number | null = 1;
  limitPrice: number | null = null;
  review = false;
  rejectionReason = '';
  private submission: { fingerprint: string; key: string } | null = null;
  private initialized = false;

  readonly visibleOrders = computed(() =>
    this.orders()
      .filter((o) => !this.accountFilter() || o.accountId === this.accountFilter())
      .filter(
        (o) =>
          this.statusFilter() === 'ALL' ||
          (this.statusFilter() === 'PENDING'
            ? this.isPending(o)
            : o.status === this.statusFilter()),
      )
      .filter((o) =>
        `${o.ticker} ${o.instrumentName} ${o.accountHolderName} ${o.orderId}`
          .toLowerCase()
          .includes(this.search().toLowerCase()),
      )
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt)),
  );
  readonly heldPositions = computed(() =>
    this.positions().filter(
      (p) => p.quantity > 0 && ['OPEN', 'PARTIALLY_CLOSED'].includes(p.status),
    ),
  );
  readonly pendingCount = computed(() => this.orders().filter((o) => this.isPending(o)).length);

  constructor() {
    effect(() => {
      const open = !!(this.confirming() || this.historyOrder());
      queueMicrotask(() => {
        if (open) {
          this.previousFocus = document.activeElement as HTMLElement;
          this.host.nativeElement
            .querySelector<HTMLElement>('[role="dialog"] button, [role="dialog"] input')
            ?.focus();
        } else this.previousFocus?.focus();
      });
    });
    this.load();
  }

  @HostListener('document:keydown', ['$event'])
  dialogKey(event: KeyboardEvent): void {
    if (!this.confirming() && !this.historyOrder()) return;
    if (event.key === 'Escape' && !this.actionId()) {
      this.confirming.set(null);
      this.historyOrder.set(null);
      return;
    }
    if (event.key !== 'Tab') return;
    const elements = Array.from(
      this.host.nativeElement.querySelectorAll<HTMLElement>(
        '[role="dialog"] button:not(:disabled), [role="dialog"] input:not(:disabled)',
      ),
    );
    const first = elements[0],
      last = elements[elements.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last?.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first?.focus();
    }
  }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    this.service
      .setup()
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (data) => {
          this.accounts.set(data.accounts);
          this.instruments.set(
            data.instruments
              .filter((i) => i.tradable)
              .sort((a, b) => a.symbol.localeCompare(b.symbol)),
          );
          const ownId = accountIdFromToken(this.auth.getToken());
          this.accountId =
            data.accounts.find((a) => String(a.id) === ownId)?.accountId ??
            data.accounts[0]?.accountId ??
            '';
          this.symbol = this.instruments()[0]?.symbol ?? '';
          if (!this.accountId) {
            this.error.set('No trading account is available.');
            this.loading.set(false);
            return;
          }
          if (!this.initialized) {
            this.initialized = true;
            timer(0, 2000)
              .pipe(
                exhaustMap(() => this.fetch()),
                takeUntilDestroyed(this.destroy),
              )
              .subscribe();
          } else this.refresh();
        },
        error: () => {
          this.error.set('Could not load trading accounts and instruments. Please retry.');
          this.loading.set(false);
        },
      });
  }

  private fetch() {
    const accountId = this.accountId;
    const symbol = this.symbol;
    return forkJoin({
      orders: this.service.orders(),
      portfolio: this.service.portfolio(accountId),
      quote: this.service.quote(symbol),
    }).pipe(
      exhaustMap((data) => {
        if (accountId !== this.accountId || symbol !== this.symbol) return EMPTY;
        this.orders.set(data.orders);
        this.account.set(data.portfolio.account);
        this.positions.set(data.portfolio.positions);
        this.quote.set(data.quote);
        this.loading.set(false);
        this.refreshError.set('');
        return EMPTY;
      }),
      catchError(() => {
        this.loading.set(false);
        this.refreshError.set('Live updates are unavailable. Retrying automatically…');
        return EMPTY;
      }),
    );
  }

  refresh(): void {
    this.fetch().pipe(takeUntilDestroyed(this.destroy)).subscribe();
  }
  changeAccount(): void {
    this.account.set(null);
    this.positions.set([]);
    this.edited();
    this.refresh();
  }
  changeSymbol(): void {
    this.quote.set(null);
    this.limitPrice = null;
    this.edited();
    this.refresh();
  }
  edited(): void {
    this.review = false;
    this.error.set('');
  }
  selectedInstrument() {
    return this.instruments().find((i) => i.symbol === this.symbol);
  }
  unitPrice(): number | null {
    return this.orderType === 'LIMIT'
      ? this.limitPrice
      : this.side === 'BUY'
        ? (this.quote()?.askPrice ?? null)
        : (this.quote()?.bidPrice ?? null);
  }
  estimatedTotal(): number {
    return (this.unitPrice() ?? 0) * (this.quantity ?? 0);
  }
  availableCash(): number {
    const reserved = this.orders()
      .filter((o) => o.accountId === this.accountId && this.isPending(o) && o.side === 'BUY')
      .reduce((sum, o) => sum + o.quantity * o.price, 0);
    return Math.max(0, (this.account()?.cashBalance ?? 0) - reserved);
  }
  availableHolding(): number {
    const held = this.heldPositions().find((p) => p.symbol === this.symbol)?.quantity ?? 0;
    const reserved = this.orders()
      .filter(
        (o) =>
          o.accountId === this.accountId &&
          this.isPending(o) &&
          o.side === 'SELL' &&
          o.ticker === this.symbol,
      )
      .reduce((sum, o) => sum + o.quantity, 0);
    return Math.max(0, held - reserved);
  }
  isPending(order: Order): boolean {
    return order.status === 'NEW' || order.status === 'PENDING';
  }
  statusLabel(status: OrderStatus): string {
    return status === 'NEW' || status === 'PENDING'
      ? 'Pending'
      : status.charAt(0) + status.slice(1).toLowerCase();
  }

  submit(form: NgForm): void {
    if (this.submitting()) return;
    if (
      form.invalid ||
      !Number.isInteger(this.quantity) ||
      !this.quantity ||
      this.quantity < 1 ||
      this.quantity > 2147483647
    ) {
      this.error.set('Choose an instrument and enter a valid whole-number quantity.');
      return;
    }
    if (!this.account() || this.account()?.status !== 'ACTIVE') {
      this.error.set('Select an active trading account.');
      return;
    }
    const price = this.unitPrice();
    if (!price || price <= 0 || !Number.isFinite(price)) {
      this.error.set(
        this.orderType === 'MARKET'
          ? 'No current quote. Choose a limit order or wait for a quote.'
          : 'Enter a positive limit price.',
      );
      return;
    }
    if (this.orderType === 'LIMIT' && Math.abs(price * 100 - Math.round(price * 100)) > 0.000001) {
      this.error.set('Limit prices allow up to two decimal places.');
      return;
    }
    if (this.side === 'BUY' && this.estimatedTotal() > this.availableCash()) {
      this.error.set('This order exceeds your available cash.');
      return;
    }
    if (this.side === 'SELL' && this.quantity > this.availableHolding()) {
      this.error.set('This order exceeds your available holdings.');
      return;
    }
    if (!this.review) {
      this.review = true;
      this.error.set('');
      return;
    }
    const draft = {
      accountId: this.accountId,
      symbol: this.symbol,
      side: this.side,
      quantity: this.quantity,
      orderType: this.orderType,
      ...(this.orderType === 'LIMIT' ? { price } : {}),
      timeInForce: 'GTC' as const,
    };
    const fingerprint = JSON.stringify(draft);
    if (this.submission?.fingerprint !== fingerprint)
      this.submission = { fingerprint, key: crypto.randomUUID() };
    const request: OrderRequest = { ...draft, idempotencyKey: this.submission!.key };
    this.submitting.set(true);
    this.error.set('');
    this.notice.set('');
    this.service
      .place(request)
      .pipe(
        finalize(() => this.submitting.set(false)),
        takeUntilDestroyed(this.destroy),
      )
      .subscribe({
        next: (order) => {
          this.orders.update((orders) => [
            order,
            ...orders.filter((o) => o.orderId !== order.orderId),
          ]);
          this.notice.set(
            `${order.side === 'BUY' ? 'Buy' : 'Sell'} order submitted: ${order.quantity} ${order.ticker}. We’ll update its status automatically.`,
          );
          this.review = false;
          this.submission = null;
          this.refresh();
        },
        error: (error) =>
          this.error.set(
            this.message(
              error,
              'Order submission failed. Check your available cash or holdings and retry.',
            ),
          ),
      });
  }

  cancel(order: Order): void {
    if (this.actionId()) return;
    this.actionId.set(order.orderId);
    this.error.set('');
    this.service
      .cancel(order.orderId)
      .pipe(
        finalize(() => this.actionId.set('')),
        takeUntilDestroyed(this.destroy),
      )
      .subscribe({
        next: (updated) => {
          this.orders.update((orders) =>
            orders.map((o) => (o.orderId === updated.orderId ? updated : o)),
          );
          this.confirming.set(null);
          this.notice.set(`Order for ${updated.ticker} cancelled.`);
          this.refresh();
        },
        error: (error) => {
          this.error.set(
            this.message(error, 'Could not cancel this order. It may already have filled.'),
          );
          this.refresh();
        },
      });
  }
  reject(order: Order): void {
    if (!this.isAdmin || this.actionId() || !this.rejectionReason.trim()) return;
    this.actionId.set(order.orderId);
    this.error.set('');
    this.service
      .reject(order.orderId, this.rejectionReason.trim())
      .pipe(
        finalize(() => this.actionId.set('')),
        takeUntilDestroyed(this.destroy),
      )
      .subscribe({
        next: () => {
          this.confirming.set(null);
          this.notice.set(`Order for ${order.ticker} rejected.`);
          this.rejectionReason = '';
          this.refresh();
        },
        error: (error) => {
          this.error.set(this.message(error, 'Could not reject the order.'));
          this.refresh();
        },
      });
  }
  showHistory(order: Order): void {
    this.historyOrder.set(order);
    this.history.set([]);
    this.historyError.set('');
    this.historyLoading.set(true);
    this.service
      .history(order.orderId)
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (history) => {
          if (this.historyOrder()?.orderId === order.orderId) {
            this.history.set(history.sort((a, b) => a.timestamp.localeCompare(b.timestamp)));
            this.historyLoading.set(false);
          }
        },
        error: () => {
          this.historyError.set('Could not load this order’s timeline.');
          this.historyLoading.set(false);
        },
      });
  }
  private message(
    error: { error?: { message?: string }; status?: number },
    fallback: string,
  ): string {
    return typeof error.error?.message === 'string'
      ? error.error.message
      : error.status === 403
        ? 'You do not have access to this account or order.'
        : fallback;
  }
  private readAdmin(): boolean {
    try {
      const part = this.auth.getToken()?.split('.')[1];
      const claims = JSON.parse(atob((part ?? '').replace(/-/g, '+').replace(/_/g, '/')));
      return Array.isArray(claims.roles) && claims.roles.includes('ADMIN');
    } catch {
      return false;
    }
  }
}
