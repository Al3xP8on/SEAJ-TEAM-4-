import {
  EMPTY,
  Observable,
  distinctUntilChanged,
  fromEvent,
  interval,
  map,
  startWith,
  switchMap,
} from 'rxjs';

/**
 * Ticks every `periodMs` while the page is visible and stops while the tab is hidden, so a
 * background tab doesn't keep calling the API. Returning to the tab ticks straight away, so the
 * numbers are fresh as soon as they can be seen.
 *
 * `immediate` controls the very first tick: off when the page has just loaded the same data itself.
 */
export function pollWhileVisible(
  document: Document,
  periodMs: number,
  { immediate }: { immediate: boolean },
): Observable<number> {
  const isVisible = () => document.visibilityState !== 'hidden';
  return fromEvent(document, 'visibilitychange').pipe(
    map(isVisible),
    startWith(isVisible()),
    distinctUntilChanged(),
    switchMap((visible, change) => {
      if (!visible) {
        return EMPTY;
      }
      const ticks = interval(periodMs);
      return change === 0 && !immediate ? ticks : ticks.pipe(startWith(-1));
    }),
  );
}
