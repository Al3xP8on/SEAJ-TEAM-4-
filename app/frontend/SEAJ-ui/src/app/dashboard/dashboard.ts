import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, catchError, exhaustMap, timer } from 'rxjs';
import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { Router } from '@angular/router';
import { Account, Position } from './dashboard.models';
import { DashboardService } from './dashboard.service';

// Holdings the user still owns; closed and liquidated positions are left out
const HELD_STATUSES: Position['status'][] = ['OPEN', 'PARTIALLY_CLOSED'];

@Component({
  selector: 'app-dashboard',
  imports: [CurrencyPipe, DecimalPipe],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class DashboardComponent implements OnInit {
  private readonly dashboard = inject(DashboardService);
  private readonly router = inject(Router);

  private readonly destroy = inject(DestroyRef);

  readonly account = signal<Account | null>(null);
  readonly positions = signal<Position[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');

  readonly holdings = computed(() =>
    this.positions()
      .filter((p) => HELD_STATUSES.includes(p.status) && p.quantity > 0)
      .map((p) => ({ ...p, value: p.quantity * p.averageCost })),
  );

  readonly holdingsValue = computed(() => this.holdings().reduce((total, h) => total + h.value, 0));

  ngOnInit(): void {
    this.load();
    timer(2000, 2000).pipe(
      exhaustMap(() => this.dashboard.load().pipe(catchError(() => EMPTY))),
      takeUntilDestroyed(this.destroy),
    ).subscribe(({ account, positions }) => {
      this.account.set(account);
      this.positions.set(positions);
    });
  }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    this.dashboard.load().pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: ({ account, positions }) => {
        this.account.set(account);
        this.positions.set(positions);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('We couldn’t load your account. Please try again.');
        this.loading.set(false);
      },
    });
  }

  goToOrderTicket(): void {
    this.router.navigate(['/order']);
  }

  goToBlotter(): void {
    this.router.navigate(['/blotter']);
  }

}
