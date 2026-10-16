import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Observable, Subject, of, throwError } from 'rxjs';
import { DashboardComponent } from './dashboard';
import { DashboardData, DashboardService } from './dashboard.service';
import { Position } from './dashboard.models';

const DATA: DashboardData = {
  account: { id: 1, accountId: 'ACC-0001', holderName: 'Alice Johnson', cashBalance: 10000, status: 'ACTIVE' },
  positions: [
    { positionId: 1, symbol: 'AAPL', quantity: 50, averageCost: 182.4, status: 'OPEN' },
    { positionId: 2, symbol: 'MSFT', quantity: 20, averageCost: 415.1, status: 'OPEN' },
    { positionId: 3, symbol: 'NVDA', quantity: 15, averageCost: 118.75, status: 'PARTIALLY_CLOSED' },
    { positionId: 4, symbol: 'TSLA', quantity: 0, averageCost: 240, status: 'CLOSED' },
  ],
};

describe('DashboardComponent', () => {
  const router = { navigate: vi.fn() };
  const dashboard = { load: vi.fn<() => Observable<DashboardData>>() };

  async function render() {
    const fixture = TestBed.createComponent(DashboardComponent);
    await fixture.whenStable();
    return { fixture, component: fixture.componentInstance, page: fixture.nativeElement as HTMLElement };
  }

  const button = (page: HTMLElement, label: string) =>
    Array.from(page.querySelectorAll('button')).find((b) => b.textContent?.trim() === label)!;

  beforeEach(async () => {
    router.navigate.mockReset();
    dashboard.load.mockReset().mockReturnValue(of(DATA));
    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        { provide: Router, useValue: router },
        { provide: DashboardService, useValue: dashboard },
      ],
    }).compileComponents();
  });

  it('welcomes the logged-in account holder by name', async () => {
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

  it('summarises holdings value and total portfolio (cash + holdings)', async () => {
    const { page } = await render();

    expect(page.querySelector('.holdings-value')?.textContent?.trim()).toBe('$19,203.25');
    expect(page.querySelector('.total-value')?.textContent?.trim()).toBe('$29,203.25'); // 10,000 + 19,203.25
  });

  it('shows a message instead of the table when there are no holdings', async () => {
    const closedOnly: Position[] = [{ positionId: 9, symbol: 'TSLA', quantity: 0, averageCost: 240, status: 'CLOSED' }];
    dashboard.load.mockReturnValue(of({ ...DATA, positions: closedOnly }));
    const { page } = await render();

    expect(page.querySelector('.positions-table')).toBeNull();
    expect(page.querySelector('.empty')?.textContent).toContain("don't hold any positions");
  });

  it('shows a loading message until the data arrives', async () => {
    const pending = new Subject<DashboardData>();
    dashboard.load.mockReturnValue(pending);
    const { fixture, page } = await render();

    expect(page.querySelector('.status')?.textContent).toContain('Loading');
    expect(page.querySelector('.welcome')).toBeNull();

    pending.next(DATA);
    pending.complete();
    await fixture.whenStable();
    expect(page.querySelector('.welcome')?.textContent).toContain('Alice Johnson');
  });

  it('shows an error with a retry that reloads the data', async () => {
    dashboard.load.mockReturnValueOnce(throwError(() => new Error('offline')));
    const { fixture, page } = await render();

    expect(page.querySelector('[role="alert"]')?.textContent).toContain("couldn’t load your account");

    button(page, 'Try again').click();
    await fixture.whenStable();
    expect(dashboard.load).toHaveBeenCalledTimes(2);
    expect(page.querySelector('.welcome')?.textContent).toContain('Alice Johnson');
  });


  it.each([
    ['Place Order', '/order'],
    ['Order History', '/blotter'],
  ])('%s button navigates to %s', async (label, path) => {
    const { page } = await render();

    button(page, label).click();

    expect(router.navigate).toHaveBeenCalledWith([path]);
  });
});
