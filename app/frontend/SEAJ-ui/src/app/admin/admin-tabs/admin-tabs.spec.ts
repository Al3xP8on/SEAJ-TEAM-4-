// The admin tab bar: links to the two admin pages, with the current one marked for screen readers.
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { AdminTabsComponent } from './admin-tabs';

@Component({ template: '' })
class EmptyPage {}

describe('Admin tabs', () => {
  async function at(url: string) {
    TestBed.configureTestingModule({
      imports: [AdminTabsComponent],
      providers: [
        provideRouter([
          { path: 'admin', component: EmptyPage },
          { path: 'admin/compliance', component: EmptyPage },
        ]),
      ],
    });
    const fixture = TestBed.createComponent(AdminTabsComponent);
    await TestBed.inject(Router).navigateByUrl(url);
    await fixture.whenStable();
    fixture.detectChanges();
    const page = fixture.nativeElement as HTMLElement;
    const current = () => page.querySelector('a[aria-current="page"]')?.textContent?.trim();
    return { page, current };
  }

  it('is a navigation landmark of links, not ARIA tabs', async () => {
    const { page } = await at('/admin');
    const nav = page.querySelector('nav')!;
    expect(nav.getAttribute('aria-label')).toBe('Administration');
    expect(page.querySelector('[role="tab"]')).toBeNull();
    expect(
      Array.from(nav.querySelectorAll('a')).map((a) => [
        a.textContent?.trim(),
        a.getAttribute('href'),
      ]),
    ).toEqual([
      ['Accounts', '/admin'],
      ['Compliance', '/admin/compliance'],
    ]);
  });

  it('marks only Accounts as current on the accounts page', async () => {
    const { page, current } = await at('/admin');
    expect(current()).toBe('Accounts');
    expect(page.querySelectorAll('a[aria-current="page"]')).toHaveLength(1);
  });

  it('marks only Compliance as current on the compliance page', async () => {
    const { page, current } = await at('/admin/compliance');
    expect(current()).toBe('Compliance');
    expect(page.querySelectorAll('a[aria-current="page"]')).toHaveLength(1);
  });
});
