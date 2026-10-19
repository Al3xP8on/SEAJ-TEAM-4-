import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TrendIconComponent } from './trend-icon';
import { TickDirective } from './tick';

@Component({
  imports: [TrendIconComponent, TickDirective],
  template: `
    <app-trend-icon [value]="change()" />
    <span class="price" [appTick]="price()">{{ price() }}</span>
  `,
})
class HostComponent {
  readonly change = signal(1.5);
  readonly price = signal<number | null>(100);
}

describe('trend cues', () => {
  let animate: ReturnType<typeof vi.fn>;

  async function render() {
    TestBed.configureTestingModule({ imports: [HostComponent] });
    const fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
    return { fixture, host: fixture.componentInstance, page: fixture.nativeElement as HTMLElement };
  }

  beforeEach(() => {
    animate = vi.fn();
    HTMLElement.prototype.animate = animate as unknown as typeof HTMLElement.prototype.animate;
  });

  afterEach(() => {
    delete (HTMLElement.prototype as Partial<HTMLElement>).animate;
    vi.unstubAllGlobals();
  });

  it('points up or down, hidden from screen readers', async () => {
    const { fixture, host, page } = await render();
    const icon = page.querySelector('app-trend-icon')!;

    expect(icon.getAttribute('aria-hidden')).toBe('true');
    expect(icon.getAttribute('data-direction')).toBe('up');

    host.change.set(-2);
    await fixture.whenStable();
    expect(icon.getAttribute('data-direction')).toBe('down');
  });

  it('tints a value green when it rises and red when it falls, but not on first show', async () => {
    const { fixture, host } = await render();
    expect(animate).not.toHaveBeenCalled();

    host.price.set(101);
    await fixture.whenStable();
    expect(animate).toHaveBeenCalledTimes(1);
    expect(JSON.stringify(animate.mock.calls[0][0])).toContain('var(--positive)');

    host.price.set(99);
    await fixture.whenStable();
    expect(JSON.stringify(animate.mock.calls[1][0])).toContain('var(--destructive)');
  });

  it('does nothing when the value is unchanged', async () => {
    const { fixture, host } = await render();

    host.price.set(100);
    await fixture.whenStable();
    expect(animate).not.toHaveBeenCalled();
  });

  it('respects a preference for reduced motion', async () => {
    vi.stubGlobal('matchMedia', (query: string) => ({ matches: query.includes('reduce') }));
    const { fixture, host } = await render();

    host.price.set(105);
    await fixture.whenStable();
    expect(animate).not.toHaveBeenCalled();
  });
});
