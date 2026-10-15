import { Component, computed, input } from '@angular/core';
import { CurrencyPipe, PercentPipe } from '@angular/common';
import { HlmCardImports } from '@spartan-ng/helm/card';

/** A holding above this share of the total portfolio is flagged */
export const CONCENTRATION_LIMIT = 0.25;

export interface CheckHolding {
  symbol: string;
  value: number;
}

export interface ConcentratedHolding {
  symbol: string;
  share: number;
}

@Component({
  selector: 'app-portfolio-check',
  imports: [CurrencyPipe, PercentPipe, HlmCardImports],
  templateUrl: './portfolio-check.html',
  styleUrl: './portfolio-check.css',
})
export class PortfolioCheckComponent {
  readonly holdings = input.required<CheckHolding[]>();
  readonly totalValue = input.required<number>();
  readonly cash = input.required<number>();
  /** Cash held back by open BUY orders; the backend only debits cash when an order fills */
  readonly reserved = input.required<number>();

  readonly limit = CONCENTRATION_LIMIT;

  readonly concentrated = computed<ConcentratedHolding[]>(() => {
    const total = this.totalValue();
    if (total <= 0) {
      return [];
    }
    return this.holdings()
      .map((h) => ({ symbol: h.symbol, share: h.value / total }))
      .filter((h) => h.share > CONCENTRATION_LIMIT)
      .sort((a, b) => b.share - a.share);
  });

  readonly coversOpenOrders = computed(() => this.cash() >= this.reserved());

  readonly warningCount = computed(
    () => this.concentrated().length + (this.coversOpenOrders() ? 0 : 1),
  );

  readonly warningLabel = computed(() => {
    const count = this.warningCount();
    return count === 0 ? 'All clear' : `${count} ${count === 1 ? 'warning' : 'warnings'}`;
  });
}
