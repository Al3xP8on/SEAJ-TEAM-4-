import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MarketTickerComponent, TickerQuote } from './market-ticker';

const QUOTES: TickerQuote[] = [
  { symbol: 'SPY', label: 'S&P 500', price: 512.4, changePercent: 0.82 },
  { symbol: 'QQQ', label: 'Nasdaq-100', price: 438.1, changePercent: -0.31 },
];

describe('MarketTickerComponent', () => {
  async function render(respond: (http: HttpTestingController) => void) {
    TestBed.configureTestingModule({
      imports: [MarketTickerComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const fixture = TestBed.createComponent(MarketTickerComponent);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    respond(http);
    await fixture.whenStable();
    return { fixture, page: fixture.nativeElement as HTMLElement };
  }

  const ok = (http: HttpTestingController) =>
    http.expectOne('/api/v1/market-data/ticker').flush(QUOTES);

  it('lists each quote with its label, price and signed change', async () => {
    const { page } = await render(ok);
    const rows = Array.from(page.querySelectorAll('ul:not(.copy) li')).map((li) =>
      li.textContent?.replace(/\s+/g, ' ').trim(),
    );

    expect(rows).toEqual(['S&P 500 $512.40 +0.82%', 'Nasdaq-100 $438.10 -0.31%']);
  });

  it('repeats the list for a seamless loop, hiding the repeat from screen readers', async () => {
    const { page } = await render(ok);
    const lists = Array.from(page.querySelectorAll('ul'));

    expect(lists.length).toBe(2);
    expect(lists[0].getAttribute('aria-hidden')).toBeNull();
    expect(lists[1].getAttribute('aria-hidden')).toBe('true');
    expect(page.querySelector('h2')?.textContent).toBe('Market prices');
  });

  it('can be paused with a labelled button', async () => {
    const { fixture, page } = await render(ok);
    const toggle = page.querySelector('button')!;

    expect(toggle.textContent?.trim()).toBe('Pause scrolling prices');
    toggle.click();
    await fixture.whenStable();

    expect(toggle.getAttribute('aria-pressed')).toBe('true');
    expect(page.querySelector('.ticker')?.classList).toContain('paused');
  });

  it('stays hidden when prices are unavailable', async () => {
    const { page } = await render((http) =>
      http
        .expectOne('/api/v1/market-data/ticker')
        .flush('down', { status: 503, statusText: 'Unavailable' }),
    );

    expect(page.querySelector('.ticker')).toBeNull();
  });
});
