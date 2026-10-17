import { HttpClient } from '@angular/common/http';
import { DOCUMENT } from '@angular/common';
import { Component, DestroyRef, computed, inject } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { EMPTY, catchError, exhaustMap, fromEvent, startWith, switchMap, timer, tap, filter, map } from 'rxjs';
import { accountIdFromToken } from '../dashboard/dashboard.service';
import { Order, TradingAccount } from '../orders/orders.models';
import { OrderNotificationsService } from '../notifications/order-notifications.service';
import { NotificationsService } from '../notifications/notifications.service';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideArrowLeftRight, lucideLayoutDashboard, lucideLogOut, lucideSettings } from '@ng-icons/lucide';
import { HlmButton } from '@spartan-ng/helm/button';

/** Keeps navigation mounted while the authenticated page changes beneath it. */
@Component({
  selector: 'app-shell',
  imports: [RouterLink, RouterLinkActive, RouterOutlet, NgIcon, HlmButton],
  providers: [provideIcons({ lucideArrowLeftRight, lucideLayoutDashboard, lucideLogOut, lucideSettings })],
  templateUrl: './shell.html',
  styleUrl: './shell.css',
})
export class ShellComponent {
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly path = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      map((event) => event.urlAfterRedirects.split(/[?#]/)[0]),
    ),
    { initialValue: this.router.url.split(/[?#]/)[0] },
  );
  readonly ordersActive = computed(() => ['/order', '/blotter'].includes(this.path()));

  private readonly orderNotifications = inject(OrderNotificationsService);
  private readonly notifications = inject(NotificationsService);
  private readonly http = inject(HttpClient, { optional: true });
  private readonly document = inject(DOCUMENT);
  private readonly destroy = inject(DestroyRef);

  constructor() {
    const http = this.http;
    const ownId = this.auth.getToken ? accountIdFromToken(this.auth.getToken()) : null;
    if (http && ownId) this.monitorOrders(http, ownId);
    this.destroy.onDestroy(() => { this.orderNotifications.reset(); this.notifications.clear(); });
  }

  private monitorOrders(http: HttpClient, ownId: string) {
    fromEvent(this.document, 'visibilitychange').pipe(
      startWith(null),
      switchMap(() => this.document.hidden ? EMPTY : timer(0, 10000).pipe(
        exhaustMap(() => this.fetchOrderUpdates(http, ownId)),
      )),
      takeUntilDestroyed(this.destroy),
    ).subscribe();
  }

  private fetchOrderUpdates(http: HttpClient, ownId: string) {
    if (!this.auth.isAuthenticated()) return EMPTY;
    return http.get<TradingAccount[]>('/api/v1/accounts').pipe(
      switchMap(accounts => {
        const account = accounts.find(account => String(account.id) === ownId);
        if (!account) return EMPTY;
        return http.get<Order[]>('/api/v1/orders').pipe(
          tap(orders => this.orderNotifications.observe(orders.filter(order => order.accountId === account.accountId))),
        );
      }),
      catchError(() => EMPTY),
    );
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
