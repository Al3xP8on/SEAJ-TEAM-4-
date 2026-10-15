import { TestBed } from '@angular/core/testing';
import { CheckHolding, PortfolioCheckComponent } from './portfolio-check';

describe('PortfolioCheckComponent', () => {
  async function render(inputs: {
    holdings: CheckHolding[];
    totalValue: number;
    cash: number;
    reserved: number;
  }) {
    const fixture = TestBed.createComponent(PortfolioCheckComponent);
    for (const [name, value] of Object.entries(inputs)) {
      fixture.componentRef.setInput(name, value);
    }
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    return {
      page,
      count: page.querySelector('.count')?.textContent?.trim(),
      checks: Array.from(page.querySelectorAll('.check')).map((c) => ({
        warning: c.classList.contains('warning'),
        title: c.querySelector('h3')?.textContent?.replace(/[⚠✓]/, '').trim(),
        text: c.querySelector('p')?.textContent?.replace(/\s+/g, ' ').trim(),
      })),
    };
  }

  beforeEach(() => TestBed.configureTestingModule({ imports: [PortfolioCheckComponent] }));

  it('is all clear for a spread portfolio whose cash covers open orders', async () => {
    const { count, checks } = await render({
      holdings: [
        { symbol: 'AAPL', value: 2000 },
        { symbol: 'MSFT', value: 2000 },
      ],
      totalValue: 10000,
      cash: 6000,
      reserved: 1500,
    });

    expect(count).toBe('All clear');
    expect(checks).toEqual([
      {
        warning: false,
        title: 'Diversification',
        text: 'No single holding is above 25% of your portfolio.',
      },
      {
        warning: false,
        title: 'Buying power',
        text: 'Cash covers your open orders ($1,500.00 reserved).',
      },
    ]);
  });

  it('flags each holding above 25% of the portfolio, largest first', async () => {
    const { count, checks } = await render({
      holdings: [
        { symbol: 'MSFT', value: 3000 },
        { symbol: 'AAPL', value: 4500 },
        { symbol: 'NVDA', value: 2500 }, // exactly 25% is within the guideline
      ],
      totalValue: 10000,
      cash: 0,
      reserved: 0,
    });

    expect(count).toBe('2 warnings');
    expect(checks.filter((c) => c.warning).map((c) => c.text)).toEqual([
      'AAPL is 45% of your portfolio, above the 25% guideline.',
      'MSFT is 30% of your portfolio, above the 25% guideline.',
    ]);
  });

  it('says so when there are no open buy orders', async () => {
    const { checks } = await render({ holdings: [], totalValue: 500, cash: 500, reserved: 0 });

    expect(checks[1].text).toBe('You have no open buy orders.');
  });

  it('warns when open buy orders are more than the cash balance', async () => {
    const { count, checks } = await render({
      holdings: [],
      totalValue: 100,
      cash: 100,
      reserved: 250,
    });

    expect(count).toBe('1 warning');
    expect(checks[1]).toEqual({
      warning: true,
      title: 'Buying power',
      text: 'Open buy orders ($250.00) are more than your cash ($100.00).',
    });
  });

  it('does not flag anything for an empty portfolio', async () => {
    const { checks } = await render({ holdings: [], totalValue: 0, cash: 0, reserved: 0 });

    expect(checks.some((c) => c.warning)).toBe(false);
  });
});
