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
import { CurrencyPipe, DatePipe, DecimalPipe, DOCUMENT, NgTemplateOutlet } from '@angular/common';
import { FormsModule, NgForm } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  EMPTY,
  catchError,
  exhaustMap,
  finalize,
  forkJoin,
  fromEvent,
  startWith,
  switchMap,
  timer,
} from 'rxjs';
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
  MarketOverview,
} from './orders.models';
import { OrdersService } from './orders.service';
import { MarketDataComponent } from './market-data';
import { MarketTickerComponent } from '../landing/market-ticker/market-ticker';
import { MarketClockComponent } from '../dashboard/market-clock/market-clock';

@Component({
  selector: 'app-orders',
  imports: [
    FormsModule,
    RouterLink,
    CurrencyPipe,
    DecimalPipe,
    DatePipe,
    MarketDataComponent,
    MarketTickerComponent,
    MarketClockComponent,
    NgTemplateOutlet,
  ],
  templateUrl: './orders.html',
  styleUrls: ['./orders.css', './order-ticket.css', './order-management.css'],
})
export class OrdersComponent {
  private readonly route = inject(ActivatedRoute);
  readonly management = this.route.snapshot.data['management'] === true;
  private readonly document = inject(DOCUMENT);
  private requestedSymbol = '';
  private readonly service = inject(OrdersService);
  private readonly auth = inject(AuthService);
  private readonly host: ElementRef<HTMLElement> = inject(ElementRef);
  private previousFocus: HTMLElement | null = null;
  private dialogWasOpen = false;
  private readonly destroy = inject(DestroyRef);
  readonly accounts = signal<TradingAccount[]>([]);
  readonly instruments = signal<Instrument[]>([]);
  readonly account = signal<Account | null>(null);
  readonly positions = signal<Position[]>([]);
  readonly orders = signal<Order[]>([]);
  readonly quote = signal<Quote | null>(null);
  readonly market = signal<MarketOverview | null>(null);
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
  readonly dateFilter = signal('ALL');
  readonly orderView = signal(this.management ? 'ACTIVE' : 'ALL');
  readonly sideFilter = signal('ALL');
  readonly sortOrder = signal('NEWEST');
  readonly pricesAsOf = computed(() => this.market()?.updatedAt ?? null);
  readonly latestTrade = computed(() => {
    const trades = (this.market()?.snapshots ?? []).map((snapshot) => snapshot.timestamp);
    return trades.length > 0
      ? trades.reduce((latest, timestamp) => (latest > timestamp ? latest : timestamp))
      : null;
  });
  readonly scopedOrders = computed(() =>
    this.orders().filter(
      (order) => !this.accountFilter() || order.accountId === this.accountFilter(),
    ),
  );
  readonly activeOrders = computed(() =>
    this.scopedOrders().filter((order) => this.isPending(order)),
  );
  readonly rejectedOrders = computed(() =>
    this.scopedOrders().filter((order) => order.status === 'REJECTED'),
  );
  readonly completedOrders = computed(() =>
    this.scopedOrders().filter((order) => ['FILLED', 'CANCELLED'].includes(order.status)),
  );
  readonly committedCash = computed(() =>
    this.activeOrders()
      .filter((order) => order.side === 'BUY')
      .reduce((sum, order) => sum + order.quantity * order.price, 0),
  );
  readonly filledOrders = computed(() =>
    this.scopedOrders().filter((order) => order.status === 'FILLED'),
  );
  readonly search = signal('');
  readonly instrumentSearch = signal('');
  readonly pickerOpen = signal(false);
  readonly activeInstrument = signal(0);
  readonly marketSymbol = signal('');
  readonly filteredInstruments = computed(() => {
    const query = this.instrumentSearch().trim().toLowerCase();
    return this.instruments().filter((instrument) =>
      `${instrument.symbol} ${instrument.name}`.toLowerCase().includes(query),
    );
  });
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
  private historyRequestVersion = 0;

  readonly visibleOrders = computed(() =>
    this.scopedOrders()
      .filter(
        (o) =>
          this.orderView() === 'ALL' ||
          (this.orderView() === 'ACTIVE'
            ? this.isPending(o)
            : this.orderView() === 'REJECTED'
              ? o.status === 'REJECTED'
              : ['FILLED', 'CANCELLED'].includes(o.status)),
      )
      .filter((o) => this.sideFilter() === 'ALL' || o.side === this.sideFilter())
      .filter((o) => this.matchesDate(o.createdAt))
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
      .sort((a, b) =>
        this.sortOrder() === 'VALUE'
          ? this.orderValue(b) - this.orderValue(a)
          : this.sortOrder() === 'OLDEST'
            ? Date.parse(a.createdAt) - Date.parse(b.createdAt)
            : Date.parse(b.createdAt) - Date.parse(a.createdAt),
      ),
  );
  readonly historyRejectionReason = computed(() => {
    if (this.historyOrder()?.status !== 'REJECTED' || this.historyLoading() || this.historyError())
      return '';
    const entry = this.history()
      .filter((entry) => entry.status === 'REJECTED')
      .reverse()
      .find((entry) => entry.reason?.trim());
    const reason = entry?.reason?.trim();
    if (
      reason ===
      'Account inactive, instrument unavailable, or insufficient cash/holdings at execution'
    ) {
      return 'A specific rejection reason was not recorded for this order.';
    }
    return reason || 'No rejection reason was recorded for this order.';
  });

  readonly heldPositions = computed(() =>
    this.positions().filter(
      (p) => p.quantity > 0 && ['OPEN', 'PARTIALLY_CLOSED'].includes(p.status),
    ),
  );
  readonly pendingCount = computed(() => this.orders().filter((o) => this.isPending(o)).length);
  readonly ordersToday = computed(() => {
    if (!this.isAdmin) return 0;
    return this.orders().filter((order) => this.isToday(order.createdAt)).length;
  });

  selectView(view: string): void {
    this.orderView.set(view);
    this.statusFilter.set('ALL');
  }

  clearFilters(): void {
    this.orderView.set('ALL');
    this.accountFilter.set('');
    this.statusFilter.set('ALL');
    this.dateFilter.set('ALL');
    this.sideFilter.set('ALL');
    this.sortOrder.set('NEWEST');
    this.search.set('');
  }

  orderValue(order: Order): number {
    return order.quantity * (order.executionPrice ?? order.price);
  }

  private matchesDate(value: string): boolean {
    if (this.dateFilter() === 'ALL') return true;
    if (this.dateFilter() === 'TODAY') return this.isToday(value);
    const start = new Date();
    start.setHours(0, 0, 0, 0);
    start.setDate(start.getDate() - (this.dateFilter() === 'WEEK' ? 6 : 29));
    const time = Date.parse(value);
    return time >= start.getTime() && time <= Date.now();
  }

  private isToday(value: string): boolean {
    const today = new Date();
    const created = new Date(value);
    return (
      created.getFullYear() === today.getFullYear() &&
      created.getMonth() === today.getMonth() &&
      created.getDate() === today.getDate()
    );
  }

  constructor() {
    effect(() => {
      const open = !!(this.confirming() || this.historyOrder());
      const wasOpen = this.dialogWasOpen;
      this.dialogWasOpen = open;
      queueMicrotask(() => {
        if (open && !wasOpen) {
          this.previousFocus = document.activeElement as HTMLElement;
          this.host.nativeElement
            .querySelector<HTMLElement>('[role="dialog"] button, [role="dialog"] input')
            ?.focus();
        } else if (!open && wasOpen) this.previousFocus?.focus();
      });
    });
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroy)).subscribe((params) => {
      this.requestedSymbol = (params.get('symbol') ?? '').trim().toUpperCase();
      if (this.initialized && this.requestedSymbol && this.requestedSymbol !== this.symbol) {
        this.selectInstrument(this.requestedSymbol);
      }
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
    (this.management ? this.service.managementSetup() : this.service.setup())
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
            (this.isAdmin ? data.accounts[0]?.accountId : '') ??
            '';
          const params = this.route.snapshot.queryParamMap;
          const requested = this.instruments().find(
            (instrument) => instrument.symbol === this.requestedSymbol,
          );
          this.symbol = requested?.symbol ?? this.instruments()[0]?.symbol ?? '';
          this.marketSymbol.set(requested?.symbol ?? '');
          if (!this.management && this.requestedSymbol && !requested) {
            this.symbol = '';
            this.error.set(
              'This instrument is no longer available for trading. Choose another instrument.',
            );
          }
          if (requested) {
            this.side = params.get('side') === 'SELL' ? 'SELL' : 'BUY';
            const requestedAccount = data.accounts.find(
              (account) => account.accountId === params.get('account'),
            );
            if (this.isAdmin && requestedAccount) this.accountId = requestedAccount.accountId;
          }
          if (!this.accountId) {
            this.error.set('No trading account is available.');
            this.loading.set(false);
            return;
          }
          if (!this.initialized) {
            this.initialized = true;
            fromEvent(this.document, 'visibilitychange')
              .pipe(
                startWith(null),
                switchMap(() =>
                  this.document.hidden
                    ? EMPTY
                    : timer(0, 10_000).pipe(exhaustMap(() => this.fetch())),
                ),
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
    if (this.management) {
      return forkJoin({ orders: this.service.orders(), market: this.service.marketOverview() }).pipe(
        exhaustMap((data) => {
          this.updateOrders(data.orders);
          this.market.set(data.market);
          this.loading.set(false);
          this.refreshError.set('');
          return EMPTY;
        }),
        catchError(() => {
          this.loading.set(false);
          this.refreshError.set('Order updates are unavailable. Retrying automatically…');
          return EMPTY;
        }),
      );
    }
    return forkJoin({
      orders: this.service.orders(),
      portfolio: this.service.portfolio(accountId),
      quote: this.service.quote(symbol),
      market: this.service.marketOverview(),
    }).pipe(
      exhaustMap((data) => {
        if (accountId !== this.accountId || symbol !== this.symbol) return EMPTY;
        this.updateOrders(data.orders);
        this.account.set(data.portfolio.account);
        this.positions.set(data.portfolio.positions);
        this.quote.set(data.quote);
        this.market.set(data.market);
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

  private updateOrders(orders: Order[]): void {
    this.orders.set(orders);
    const viewed = this.historyOrder();
    const updated = orders.find((order) => order.orderId === viewed?.orderId);
    if (viewed && updated && viewed.status !== updated.status) this.showHistory(updated);
    const cancelling = this.confirming();
    const latest = orders.find((order) => order.orderId === cancelling?.orderId);
    if (latest) this.confirming.set(latest);
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
    this.marketSymbol.set(this.symbol);
    this.quote.set(null);
    this.limitPrice = null;
    this.edited();
    this.refresh();
  }
  selectInstrument(symbol: string): void {
    if (this.submitting() || !this.instruments().some((instrument) => instrument.symbol === symbol))
      return;
    this.symbol = symbol;
    this.pickerOpen.set(false);
    this.instrumentSearch.set('');
    this.changeSymbol();
  }
  openInstrumentPicker(): void {
    this.instrumentSearch.set('');
    this.activeInstrument.set(
      Math.max(
        0,
        this.instruments().findIndex((instrument) => instrument.symbol === this.symbol),
      ),
    );
    this.pickerOpen.set(true);
    this.scrollActiveInstrument();
  }
  searchInstruments(query: string): void {
    this.instrumentSearch.set(query);
    this.activeInstrument.set(0);
    this.pickerOpen.set(true);
  }
  closeInstrumentPicker(event: FocusEvent): void {
    const wrapper = event.currentTarget as HTMLElement;
    if (!wrapper.contains(event.relatedTarget as Node | null)) this.pickerOpen.set(false);
  }
  instrumentKey(event: KeyboardEvent): void {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      if (!this.pickerOpen()) this.openInstrumentPicker();
      else {
        const count = this.filteredInstruments().length;
        this.activeInstrument.set(
          Math.max(
            0,
            Math.min(count - 1, this.activeInstrument() + (event.key === 'ArrowDown' ? 1 : -1)),
          ),
        );
        this.scrollActiveInstrument();
      }
    } else if (event.key === 'Enter' && this.pickerOpen()) {
      event.preventDefault();
      const instrument = this.filteredInstruments()[this.activeInstrument()];
      if (instrument) this.selectInstrument(instrument.symbol);
    } else if (event.key === 'Escape') {
      event.preventDefault();
      this.pickerOpen.set(false);
    } else if (event.key === 'Tab') this.pickerOpen.set(false);
  }
  private scrollActiveInstrument(): void {
    queueMicrotask(() =>
      this.host.nativeElement
        .querySelector<HTMLElement>(`#instrument-option-${this.activeInstrument()}`)
        ?.scrollIntoView?.({ block: 'nearest' }),
    );
  }
  edited(): void {
    this.review = false;
    this.error.set('');
  }
  selectedInstrument() {
    return this.instruments().find((i) => i.symbol === this.symbol);
  }
  unitPrice(): number | null {
    if (this.orderType === 'LIMIT') return this.limitPrice;
    return this.side === 'BUY'
      ? (this.quote()?.askPrice ?? null)
      : (this.quote()?.bidPrice ?? null);
  }
  estimatedTotal(): number {
    return (this.unitPrice() ?? 0) * (this.quantity ?? 0);
  }
  reservedCash(): number {
    const reserved = this.orders()
      .filter((o) => o.accountId === this.accountId && this.isPending(o) && o.side === 'BUY')
      .reduce((sum, o) => sum + Math.round(o.quantity * o.price * 100), 0);
    return reserved / 100;
  }
  availableCash(): number {
    return Math.max(
      0,
      Math.round(((this.account()?.cashBalance ?? 0) - this.reservedCash()) * 100) / 100,
    );
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
      !this.selectedInstrument() ||
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
    const price = this.unitPrice() ?? 0;
    const validationError = this.priceAndBalanceError(price);
    if (validationError) {
      this.error.set(validationError);
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
    if (this.submission?.fingerprint !== fingerprint) {
      try {
        this.submission = { fingerprint, key: this.createSubmissionKey() };
      } catch {
        this.error.set('Could not prepare your order. Reload the page and try again.');
        return;
      }
    }
    const request: OrderRequest = { ...draft, idempotencyKey: this.submission.key };
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
            `${order.side === 'BUY' ? 'Buy' : 'Sell'} order submitted: ${order.quantity} ${order.ticker}.`,
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

  private priceAndBalanceError(price: number): string {
    if (price <= 0 || !Number.isFinite(price)) {
      return this.orderType === 'MARKET'
        ? 'No current quote. Choose a limit order or wait for a quote.'
        : 'Enter a positive limit price.';
    }
    if (this.orderType === 'LIMIT' && Math.abs(price * 100 - Math.round(price * 100)) > 0.000001) {
      return 'Limit prices allow up to two decimal places.';
    }
    if (this.side === 'BUY' && this.estimatedTotal() > this.availableCash()) {
      return 'This order exceeds your available cash.';
    }
    if (this.side === 'SELL' && (this.quantity ?? 0) > this.availableHolding()) {
      return 'This order exceeds your available holdings.';
    }
    return '';
  }

  private createSubmissionKey(): string {
    const browserCrypto = globalThis.crypto;
    if (typeof browserCrypto?.randomUUID === 'function') return browserCrypto.randomUUID();
    // getRandomValues remains available on HTTP origins where randomUUID is unavailable.
    const bytes = browserCrypto.getRandomValues(new Uint8Array(16));
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
    return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
  }

  beginCancel(order: Order): void {
    if (!this.isPending(order)) return;
    this.historyOrder.set(null);
    this.confirming.set(order);
    this.rejectionReason = '';
  }

  cancel(order: Order): void {
    if (this.actionId() || !this.isPending(order)) return;
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
    if (!this.isAdmin || this.actionId() || !this.isPending(order) || !this.rejectionReason.trim())
      return;
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
    const version = ++this.historyRequestVersion;
    this.historyOrder.set(order);
    this.history.set([]);
    this.historyError.set('');
    this.historyLoading.set(true);
    this.service
      .history(order.orderId)
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (history) => {
          if (
            version === this.historyRequestVersion &&
            this.historyOrder()?.orderId === order.orderId
          ) {
            this.history.set(history.sort((a, b) => a.timestamp.localeCompare(b.timestamp)));
            this.historyLoading.set(false);
          }
        },
        error: () => {
          if (
            version !== this.historyRequestVersion ||
            this.historyOrder()?.orderId !== order.orderId
          )
            return;
          this.historyError.set('Could not load this order’s timeline.');
          this.historyLoading.set(false);
        },
      });
  }
  private message(
    error: { error?: { message?: string }; status?: number },
    fallback: string,
  ): string {
    if (typeof error.error?.message === 'string') return error.error.message;
    if (error.status === 403) return 'You do not have access to this account or order.';
    return fallback;
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
