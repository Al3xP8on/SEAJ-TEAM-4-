import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { DashboardComponent } from './dashboard';
import { Position } from './dashboard.models';

describe('DashboardComponent', () => {
  const router = { navigate: vi.fn() };

  async function render() {
    const fixture = TestBed.createComponent(DashboardComponent);
    await fixture.whenStable();
    return { fixture, component: fixture.componentInstance, page: fixture.nativeElement as HTMLElement };
  }

  beforeEach(async () => {
    router.navigate.mockReset();
    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [{ provide: Router, useValue: router }],
    }).compileComponents();
  });

  it('welcomes the account holder by name', async () => {
    const { page } = await render();

    expect(page.querySelector('.welcome')?.textContent).toContain('Welcome, Alice Johnson');
  });

  it('shows the cash balance as currency', async () => {
    const { page } = await render();

    expect(page.querySelector('.balance')?.textContent?.trim()).toBe('$10,000.00');
  });

  it('lists only held positions, with quantity and value', async () => {
    const { page } = await render();
    const rows = Array.from(page.querySelectorAll('.positions-table tbody tr'));

    expect(rows.map((r) => r.querySelector('.symbol')?.textContent)).toEqual(['AAPL', 'MSFT', 'NVDA']);
    expect(rows[0].textContent).toContain('50');
    expect(rows[0].textContent).toContain('$9,120.00'); // 50 x 182.40
  });

  it('totals the value of all holdings', async () => {
    const { page } = await render();

    // 9,120.00 + 8,302.00 + 1,781.25
    expect(page.querySelector('.total')?.textContent?.trim()).toBe('$19,203.25');
  });

  it('shows a message instead of the table when there are no holdings', async () => {
    const { fixture, component, page } = await render();
    const closedOnly: Position[] = [{ positionId: 9, symbol: 'TSLA', quantity: 0, averageCost: 240, status: 'CLOSED' }];

    component.positions.set(closedOnly);
    await fixture.whenStable();

    expect(page.querySelector('.positions-table')).toBeNull();
    expect(page.querySelector('.empty')?.textContent).toContain("don't hold any positions");
  });

  it.each([
    ['Place Order', '/order'],
    ['Order History', '/blotter'],
    ['Logout', '/login'],
  ])('%s button navigates to %s', async (label, path) => {
    const { page } = await render();
    const button = Array.from(page.querySelectorAll('button')).find((b) => b.textContent?.trim() === label);

    button!.click();

    expect(router.navigate).toHaveBeenCalledWith([path]);
  });
});
