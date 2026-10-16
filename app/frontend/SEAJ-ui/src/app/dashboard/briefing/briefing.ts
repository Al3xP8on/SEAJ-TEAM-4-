import { Component, computed, input } from '@angular/core';
import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  lucideChartPie,
  lucideClock,
  lucideTrendingDown,
  lucideTrendingUp,
  lucideTriangleAlert,
  lucideWallet,
} from '@ng-icons/lucide';
import { HlmCardImports } from '@spartan-ng/helm/card';
import {
  HlmItem,
  HlmItemContent,
  HlmItemDescription,
  HlmItemMedia,
  HlmItemTitle,
} from '@spartan-ng/helm/item';
import { BriefingFacts, writeBriefing } from './write-briefing';

@Component({
  selector: 'app-briefing',
  imports: [
    HlmCardImports,
    HlmItem,
    HlmItemContent,
    HlmItemDescription,
    HlmItemMedia,
    HlmItemTitle,
    NgIcon,
  ],
  providers: [
    provideIcons({
      lucideChartPie,
      lucideClock,
      lucideTrendingDown,
      lucideTrendingUp,
      lucideTriangleAlert,
      lucideWallet,
    }),
  ],
  templateUrl: './briefing.html',
  styleUrl: './briefing.css',
})
export class BriefingComponent {
  readonly facts = input.required<BriefingFacts>();

  readonly items = computed(() => writeBriefing(this.facts()));
}
