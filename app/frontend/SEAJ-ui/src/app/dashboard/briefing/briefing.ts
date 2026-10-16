import { Component, computed, input } from '@angular/core';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { BriefingFacts, writeBriefing } from './write-briefing';

@Component({
  selector: 'app-briefing',
  imports: [HlmCardImports],
  template: `
    <section hlmCard class="briefing" aria-labelledby="briefing-title">
      <div hlmCardHeader>
        <h2 hlmCardTitle id="briefing-title">At a glance</h2>
      </div>
      <div hlmCardContent>
        <p class="sentences">
          @for (sentence of sentences(); track $index) {
            <span>{{ sentence }}</span
            >{{ ' ' }}
          }
        </p>
      </div>
    </section>
  `,
  styleUrl: './briefing.css',
})
export class BriefingComponent {
  readonly facts = input.required<BriefingFacts>();

  readonly sentences = computed(() => writeBriefing(this.facts()));
}
