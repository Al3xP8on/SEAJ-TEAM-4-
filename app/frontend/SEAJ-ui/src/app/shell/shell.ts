import { HttpClient } from '@angular/common/http';
import { DOCUMENT } from '@angular/common';
import { DestroyRef } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, catchError, exhaustMap, fromEvent, startWith, switchMap, timer, tap } from 'rxjs';
import { accountIdFromToken } from '../dashboard/dashboard.service';
import { Order, TradingAccount } from '../orders/orders.models';
import { OrderNotificationsService } from '../notifications/order-notifications.service';
import { NotificationsService } from '../notifications/notifications.service';
import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';
import { AuthService } from '../auth/auth.service';

/** Keeps navigation mounted while the authenticated page changes beneath it. */
@Component({
  selector: 'app-shell',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
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
    if (http && ownId) {
      fromEvent(this.document, 'visibilitychange').pipe(
        startWith(null),
        switchMap(() => this.document.hidden ? EMPTY : timer(0, 10000).pipe(
          exhaustMap(() => this.auth.isAuthenticated() ? http.get<TradingAccount[]>('/api/v1/accounts').pipe(
            switchMap(accounts => {
              const account = accounts.find(account => String(account.id) === ownId);
              return account ? http.get<Order[]>('/api/v1/orders').pipe(
                tap(orders => this.orderNotifications.observe(orders.filter(order => order.accountId === account.accountId))),
              ) : EMPTY;
            }),
            catchError(() => EMPTY),
          ) : EMPTY),
        )),
        takeUntilDestroyed(this.destroy),
      ).subscribe();
    }
    this.destroy.onDestroy(() => { this.orderNotifications.reset(); this.notifications.clear(); });
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
