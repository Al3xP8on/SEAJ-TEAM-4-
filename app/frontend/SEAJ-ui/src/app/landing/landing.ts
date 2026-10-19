import { Component, DOCUMENT, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  lucideAccessibility,
  lucideArrowLeftRight,
  lucideArrowRight,
  lucideCircleCheck,
  lucideEye,
  lucideMailCheck,
  lucideSend,
  lucideUserPlus,
  lucideWallet,
} from '@ng-icons/lucide';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { ProductPreviewComponent } from './product-preview/product-preview';
import { MarketTickerComponent } from './market-ticker/market-ticker';
import { describeNextChange, marketStatus } from '../dashboard/market-status';
import { pollWhileVisible } from '../dashboard/poll-while-visible';
import { AuthService } from '../auth/auth.service';

/**
 * The public landing page. Every claim here describes something the platform actually does;
 * keep it that way when editing.
 */
@Component({
  selector: 'app-landing',
  imports: [
    RouterLink,
    NgIcon,
    HlmButton,
    HlmCardImports,
    ProductPreviewComponent,
    MarketTickerComponent,
  ],
  providers: [
    provideIcons({
      lucideAccessibility,
      lucideArrowLeftRight,
      lucideArrowRight,
      lucideCircleCheck,
      lucideEye,
      lucideMailCheck,
      lucideSend,
      lucideUserPlus,
      lucideWallet,
    }),
  ],
  templateUrl: './landing.html',
  styleUrl: './landing.css',
})
export class LandingComponent {
  readonly benefits = [
    'Live prices on every trade',
    'Track each order as it fills',
    'Your whole portfolio on one screen',
  ];

  private readonly document = inject(DOCUMENT);

  /** A signed-in visitor gets a link back to their portfolio instead of log in and sign up */
  readonly signedIn = inject(AuthService).isAuthenticated();

  /** Without a login there's no trade data, so this works from the regular schedule */
  private readonly now = signal(new Date());

  readonly market = computed(() => {
    const status = marketStatus(this.now(), null);
    return { status, change: describeNextChange(this.now(), status) };
  });

  /**
   * Scrolls smoothly to a section (instantly for people who prefer reduced motion) and moves
   * keyboard focus to its heading, so the next Tab continues from there.
   */
  goToSection(event: Event, id: string): void {
    const section = this.document.getElementById(id);
    if (!section) {
      return;
    }
    event.preventDefault();
    const reduceMotion = this.document.defaultView?.matchMedia?.(
      '(prefers-reduced-motion: reduce)',
    ).matches;
    section.scrollIntoView({ behavior: reduceMotion ? 'auto' : 'smooth' });
    section.querySelector<HTMLElement>('h2')?.focus({ preventScroll: true });
    history.replaceState(null, '', `#${id}`);
  }

  constructor() {
    pollWhileVisible(this.document, 30_000, { immediate: true })
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.now.set(new Date()));
  }

  readonly features = [
    {
      id: 'feature-orders',
      icon: 'lucideArrowLeftRight',
      title: 'Market and limit orders',
      text: "Buy at today's price, or set your own and wait for it.",
    },
    {
      id: 'feature-watchlist',
      icon: 'lucideEye',
      title: 'Watchlist',
      text: "Keep the stocks you're interested in on one list.",
    },
    {
      id: 'feature-everyone',
      icon: 'lucideAccessibility',
      title: 'Built for everyone',
      text: 'Use a keyboard or screen reader, read charts as tables, or switch to dark mode.',
    },
  ];

  readonly steps = [
    { icon: 'lucideUserPlus', title: 'Sign up', text: 'Pick a username and password.' },
    { icon: 'lucideMailCheck', title: 'Confirm your email', text: 'Enter the code we email you.' },
    { icon: 'lucideWallet', title: 'Add funds', text: 'Deposit from your account page.' },
    { icon: 'lucideSend', title: 'Trade', text: 'Search for a stock and place your order.' },
  ];
}
