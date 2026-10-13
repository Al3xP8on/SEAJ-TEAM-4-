import { Component, computed, signal } from '@angular/core';
import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { Router } from '@angular/router';
import { Account, Position } from './dashboard.models';

// Sample data for the UI mockup; replaced by the account API once the session work is in
const SAMPLE_ACCOUNT: Account = {
  id: 1,
  accountId: 'ACC-0001',
  holderName: 'Alice Johnson',
  cashBalance: 10000,
  status: 'ACTIVE',
};

const SAMPLE_POSITIONS: Position[] = [
  { positionId: 1, symbol: 'AAPL', quantity: 50, averageCost: 182.4, status: 'OPEN' },
  { positionId: 2, symbol: 'MSFT', quantity: 20, averageCost: 415.1, status: 'OPEN' },
  { positionId: 3, symbol: 'NVDA', quantity: 15, averageCost: 118.75, status: 'PARTIALLY_CLOSED' },
  { positionId: 4, symbol: 'TSLA', quantity: 0, averageCost: 240, status: 'CLOSED' },
];

// Holdings the user still owns; closed and liquidated positions are left out
const HELD_STATUSES: Position['status'][] = ['OPEN', 'PARTIALLY_CLOSED'];

@Component({
  selector: 'app-dashboard',
  imports: [CurrencyPipe, DecimalPipe],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class DashboardComponent {
  readonly account = signal<Account>(SAMPLE_ACCOUNT);
  readonly positions = signal<Position[]>(SAMPLE_POSITIONS);

  readonly holdings = computed(() =>
    this.positions()
      .filter((p) => HELD_STATUSES.includes(p.status) && p.quantity > 0)
      .map((p) => ({ ...p, value: p.quantity * p.averageCost })),
  );

  readonly holdingsValue = computed(() => this.holdings().reduce((total, h) => total + h.value, 0));

  constructor(private router: Router) {}

  goToOrderTicket(): void {
    this.router.navigate(['/order']);
  }

  goToBlotter(): void {
    this.router.navigate(['/blotter']);
  }

  logout(): void {
    // Session clearing is wired in with the auth work; for now just return to login
    this.router.navigate(['/login']);
  }
}
