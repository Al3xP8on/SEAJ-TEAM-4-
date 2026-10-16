import { TestBed } from '@angular/core/testing';
import { LineChartComponent, LinePoint } from './line-chart';

const POINTS: LinePoint[] = [
  { time: '2026-10-15T14:30:00Z', value: 100 },
  { time: '2026-10-15T14:31:00Z', value: 102.5 },
  { time: '2026-10-15T14:32:00Z', value: 101 },
];

describe('LineChartComponent', () => {
  async function render(inputs: Record<string, unknown>) {
    TestBed.configureTestingModule({ imports: [LineChartComponent] });
    const fixture = TestBed.createComponent(LineChartComponent);
    fixture.componentRef.setInput('points', POINTS);
    fixture.componentRef.setInput('description', 'Test values');
    for (const [name, value] of Object.entries(inputs)) {
      fixture.componentRef.setInput(name, value);
    }
    await fixture.whenStable();
    return { fixture, page: fixture.nativeElement as HTMLElement };
  }

  const tableRows = (page: HTMLElement) =>
    Array.from(page.querySelectorAll('details tbody tr')).map((r) =>
      Array.from(r.children)
        .map((cell) => cell.textContent?.trim())
        .join(' '),
    );

  it('offers every value as a table, with the given column label', async () => {
    const { page } = await render({ valueLabel: 'Close' });

    const headings = Array.from(page.querySelectorAll('details thead th')).map((th) =>
      th.textContent?.trim(),
    );
    expect(headings).toEqual(['Date', 'Close']);
    expect(tableRows(page).map((r) => r.split(' ').at(-1))).toEqual([
      '$100.00',
      '$102.50',
      '$101.00',
    ]);
    expect(page.querySelector('details caption')?.textContent?.trim()).toBe('Test values');
  });

  it('labels the first column by time for intraday data', async () => {
    const { page } = await render({ dateFormat: 'HH:mm' });

    expect(page.querySelector('details thead th')?.textContent?.trim()).toBe('Time');
  });

  it('clears the inspected point when new data arrives', async () => {
    const { fixture, page } = await render({});
    page.querySelector('svg')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Home' }));
    await fixture.whenStable();
    expect(page.querySelector('circle.dot')).not.toBeNull();

    fixture.componentRef.setInput('points', [...POINTS]);
    await fixture.whenStable();
    expect(page.querySelector('circle.dot')).toBeNull();
  });
});
