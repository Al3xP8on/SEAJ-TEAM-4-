import { Component } from '@angular/core';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideArrowDownRight, lucideArrowUpRight } from '@ng-icons/lucide';

/**
 * A drawn preview of the dashboard for the landing page: the summary, a price line and a few
 * holdings. The figures are a sample to show the product, not anyone's results, so screen readers
 * get one description instead of the numbers.
 */
@Component({
  selector: 'app-product-preview',
  imports: [NgIcon],
  providers: [provideIcons({ lucideArrowUpRight, lucideArrowDownRight })],
  templateUrl: './product-preview.html',
  styleUrl: './product-preview.css',
  host: {
    role: 'img',
    'aria-label': 'Preview of the SEAJ dashboard with a portfolio summary, chart and holdings',
  },
})
export class ProductPreviewComponent {
  readonly holdings = [
    { symbol: 'AAPL', name: 'Apple Inc.', value: '$9,500.00', change: '+2.64%', up: true },
    { symbol: 'MSFT', name: 'Microsoft', value: '$8,000.00', change: '-1.22%', up: false },
    { symbol: 'NVDA', name: 'NVIDIA', value: '$2,250.00', change: '+4.10%', up: true },
  ];

  // A rising line with a few dips, drawn in a 300 x 90 box
  readonly line =
    '0,72 20,68 40,70 60,60 80,63 100,52 120,55 140,46 160,49 180,38 200,41 220,30 240,33 260,22 280,25 300,14';
  readonly area = `M0,90 L${this.line.replaceAll(' ', ' L')} L300,90 Z`;
}
