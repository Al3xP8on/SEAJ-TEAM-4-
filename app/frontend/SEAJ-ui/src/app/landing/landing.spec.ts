import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { LandingComponent } from './landing';
import { AuthService } from '../auth/auth.service';

describe('LandingComponent', () => {
  async function render({ signedIn = false } = {}) {
    TestBed.configureTestingModule({
      imports: [LandingComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { isAuthenticated: () => signedIn } },
      ],
    });
    const fixture = TestBed.createComponent(LandingComponent);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  const texts = (page: HTMLElement, selector: string) =>
    Array.from(page.querySelectorAll(selector)).map((el) =>
      el.textContent?.replace(/\s+/g, ' ').trim(),
    );

  it('has one main heading, then section headings', async () => {
    const page = await render();

    expect(texts(page, 'h1')).toEqual(['Trade stocks and ETFs in one place']);
    expect(texts(page, 'h2')).toEqual(['What you get', 'How it works']);
  });

  it('uses links, not buttons, to reach the login page', async () => {
    const page = await render();

    expect(texts(page, 'a[href="/login"]')).toEqual(['Log in', 'Log in']);
    expect(texts(page, 'a[href="/login?mode=register"]')).toEqual(['Sign up', 'Create an account']);
    expect(page.querySelector('button')).toBeNull();
  });

  it('offers a signed-in visitor their portfolio instead of log in and sign up', async () => {
    const page = await render({ signedIn: true });

    expect(texts(page, 'a[href="/dashboard"]')).toEqual(['Go to portfolio', 'Go to portfolio']);
    expect(page.querySelector('a[href^="/login"]')).toBeNull();
  });

  it('pairs the logo with the name as one link home', async () => {
    const page = await render();
    const brand = page.querySelector('header a.brand')!;

    expect(brand.getAttribute('href')).toBe('/');
    expect(brand.getAttribute('aria-label')).toBe('SEAJ Capital, home');
    expect(brand.querySelector('.brand-name')?.textContent).toBe('SEAJ Capital');
  });

  it('links to sections that exist on the page', async () => {
    const page = await render();
    const targets = Array.from(page.querySelectorAll<HTMLAnchorElement>('nav.sections a')).map(
      (a) => a.getAttribute('href'),
    );

    expect(targets).toEqual(['#features', '#how-it-works']);
    for (const target of targets) {
      expect(page.querySelector(target!)).not.toBeNull();
    }
  });

  it('describes the features and steps under headings, with decorative icons', async () => {
    const page = await render();

    expect(texts(page, '.feature-card h3')).toEqual([
      'Market and limit orders',
      'Watchlist',
      'Built for everyone',
    ]);
    expect(texts(page, '.steps h3')).toEqual([
      'Sign up',
      'Confirm your email',
      'Add funds',
      'Trade',
    ]);
    const icons = Array.from(page.querySelectorAll('main ng-icon')).filter(
      (icon) => !icon.closest('app-product-preview'),
    );
    expect(icons.every((icon) => icon.getAttribute('aria-hidden') === 'true')).toBe(true);
  });

  it('leads with three true benefits', async () => {
    const page = await render();

    expect(texts(page, '.benefits li')).toEqual([
      'Live prices on every trade',
      'Track each order as it fills',
      'Your whole portfolio on one screen',
    ]);
  });

  it('shows the product as one described image, not a list of sample numbers', async () => {
    const page = await render();
    const preview = page.querySelector('app-product-preview')!;

    expect(preview.getAttribute('role')).toBe('img');
    expect(preview.getAttribute('aria-label')).toContain('Preview of the SEAJ dashboard');
    expect(preview.querySelector('.window')?.getAttribute('aria-hidden')).toBe('true');
  });

  it('pins the live NYSE status to the preview, based on the regular schedule', async () => {
    const page = await render();
    const chip = page.querySelector('.market-chip')?.textContent?.replace(/\s+/g, ' ').trim();

    expect(chip).toMatch(/^NYSE (open|closed) · (opens|closes) .+ \(regular hours\)$/);
  });

  it('keeps the wording about trading, not about how the system works', async () => {
    const page = await render();

    for (const word of ['Kafka', 'Alpaca', 'engine', 'verification code']) {
      expect(page.textContent).not.toContain(word);
    }
  });

  it('scrolls to a section and moves focus to its heading', async () => {
    const page = await render();
    document.body.appendChild(page);
    const scroll = vi.fn();
    Element.prototype.scrollIntoView = scroll;

    (page.querySelector('nav.sections a[href="#how-it-works"]') as HTMLAnchorElement).click();

    expect(scroll).toHaveBeenCalledWith({ behavior: 'smooth' });
    expect(document.activeElement?.textContent).toBe('How it works');
    page.remove();
  });

  it('keeps feature cards out of the landmark list', async () => {
    const page = await render();
    const cards = Array.from(page.querySelectorAll('.feature-card'));

    expect(cards.map((card) => card.tagName)).toEqual(['ARTICLE', 'ARTICLE', 'ARTICLE']);
  });

  it('loads no images from other sites', async () => {
    const page = await render();
    const external = Array.from(page.querySelectorAll('img')).filter((img) =>
      /^https?:/.test(img.getAttribute('src') ?? ''),
    );

    expect(external).toEqual([]);
  });
});
