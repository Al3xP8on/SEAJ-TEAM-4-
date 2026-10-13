import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';
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
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

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
  }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    this.dashboard.load().subscribe({
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

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
