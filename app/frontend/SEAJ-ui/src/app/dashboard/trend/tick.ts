import { Directive, ElementRef, effect, inject, input } from '@angular/core';

const DURATION_MS = 1200;

/**
 * Briefly tints the element green or red when its value rises or falls between refreshes, like a
 * trading terminal but gentle: one soft fade, at most once per refresh. Skipped entirely for
 * people who ask for reduced motion, and never the only cue, as the value itself has changed.
 */
@Directive({ selector: '[appTick]' })
export class TickDirective {
  /** The number to watch; null means no value yet */
  readonly appTick = input<number | null>(null);

  constructor() {
    const element = inject(ElementRef<HTMLElement>).nativeElement as HTMLElement;
    let previous: number | null = null;
    effect(() => {
      const value = this.appTick();
      if (value !== null && previous !== null && value !== previous) {
        flash(element, value > previous ? 'var(--positive)' : 'var(--destructive)');
      }
      previous = value;
    });
  }
}

function flash(element: HTMLElement, colour: string): void {
  const reduceMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
  if (reduceMotion || typeof element.animate !== 'function') {
    return;
  }
  element.animate(
    [
      { backgroundColor: `color-mix(in srgb, ${colour} 22%, transparent)` },
      { backgroundColor: 'transparent' },
    ],
    { duration: DURATION_MS, easing: 'ease-out' },
  );
}
