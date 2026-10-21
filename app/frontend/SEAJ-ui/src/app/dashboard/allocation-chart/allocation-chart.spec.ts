import { TestBed } from '@angular/core/testing';
import { AllocationChartComponent, AllocationSlice } from './allocation-chart';

describe('AllocationChartComponent', () => {
  async function render(slices: AllocationSlice[]) {
    await TestBed.configureTestingModule({
      imports: [AllocationChartComponent],
    }).compileComponents();
    const fixture = TestBed.createComponent(AllocationChartComponent);
    fixture.componentRef.setInput('slices', slices);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  const rows = (page: HTMLElement) =>
    Array.from(page.querySelectorAll('.legend li')).map((li) => [
      li.querySelector('.label')?.textContent?.trim(),
      li.querySelector('.percent')?.textContent?.trim(),
    ]);

  it('lists every slice with its share of the total as text', async () => {
    const page = await render([
      { label: 'AAPL', value: 300 },
      { label: 'MSFT', value: 100 },
      { label: 'Cash', value: 600 },
    ]);

    expect(rows(page)).toEqual([
      ['AAPL', '30%'],
      ['MSFT', '10%'],
      ['Cash', '60%'],
    ]);
  });

  it('hides the drawn chart from screen readers, since the legend carries the data', async () => {
    const page = await render([{ label: 'Cash', value: 1 }]);

    expect(page.querySelector('.chart')?.getAttribute('aria-hidden')).toBe('true');
  });

  it('shows 0% rather than failing when everything is zero', async () => {
    const page = await render([{ label: 'Cash', value: 0 }]);

    expect(rows(page)).toEqual([['Cash', '0%']]);
  });
});
