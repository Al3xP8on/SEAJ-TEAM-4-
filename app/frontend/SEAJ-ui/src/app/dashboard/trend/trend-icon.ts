import { Component, input } from '@angular/core';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideArrowDownRight, lucideArrowUpRight } from '@ng-icons/lucide';

/**
 * A small arrow showing whether a change is up or down. Decoration only: the value next to it
 * always carries its sign in text, so it's hidden from screen readers.
 */
@Component({
  selector: 'app-trend-icon',
  imports: [NgIcon],
  providers: [provideIcons({ lucideArrowUpRight, lucideArrowDownRight })],
  template: `<ng-icon [name]="value() < 0 ? 'lucideArrowDownRight' : 'lucideArrowUpRight'" />`,
  host: { 'aria-hidden': 'true', '[attr.data-direction]': "value() < 0 ? 'down' : 'up'" },
  styles: `
    :host {
      display: inline-flex;
      vertical-align: -0.15em;
      margin-right: 2px;
      font-size: 1.05em;
    }
  `,
})
export class TrendIconComponent {
  readonly value = input.required<number>();
}
