import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { App } from '../app';
import { routes } from '../app.routes';
import { AuthService } from '../auth/auth.service';

@Component({ template: '<p>Page content</p>' })
class TestPage {}

@Component({ template: '<p>Login</p>' })
class TestLogin {}

// Keep the real route hierarchy and guards, with lightweight page content.
const testRoutes = routes.map((route) => ({
  ...route,
  ...(route.path === 'login' ? { component: TestLogin } : {}),
  ...(route.children
    ? { children: route.children.map((child) => ({ ...child, component: TestPage })) }
    : {}),
}));

describe('Shared navigation', () => {
  const auth = { isAuthenticated: vi.fn(), logout: vi.fn() };

  beforeEach(async () => {
    auth.isAuthenticated.mockReset().mockReturnValue(true);
    auth.logout.mockReset().mockImplementation(() => auth.isAuthenticated.mockReturnValue(false));
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter(testRoutes), { provide: AuthService, useValue: auth }],
    }).compileComponents();
  });

  async function render(path: string) {
    const fixture = TestBed.createComponent(App);
    const router = TestBed.inject(Router);
    await fixture.whenStable();
    await router.navigateByUrl(path);
    await fixture.whenStable();
    return { fixture, router, page: fixture.nativeElement as HTMLElement };
  }

  it('keeps the same navigation element across overview, orders and history', async () => {
    const { fixture, router, page } = await render('/dashboard');
    const header = page.querySelector('header');
    expect(header).not.toBeNull();
    await router.navigateByUrl('/order');
    await fixture.whenStable();
    expect(page.querySelector('header')).toBe(header);
    await router.navigateByUrl('/blotter');
    await fixture.whenStable();
    expect(page.querySelector('header')).toBe(header);
    expect(page.querySelectorAll('header')).toHaveLength(1);
  });

  it('marks overview and both order routes correctly in the navigation', async () => {
    const { fixture, router, page } = await render('/dashboard');
    const active = () => page.querySelector('nav [aria-current="page"]')?.textContent?.trim();
    expect(active()).toBe('Portfolio');
    await router.navigateByUrl('/order');
    await fixture.whenStable();
    expect(active()).toBe('Orders');
    await router.navigateByUrl('/blotter');
    await fixture.whenStable();
    expect(active()).toBe('Orders');
  });

  it('takes the logo back to the portfolio while signed in', async () => {
    const { page } = await render('/order');
    const brand = page.querySelector('header a.brand')!;
    expect(brand.getAttribute('href')).toBe('/dashboard');
    expect(brand.getAttribute('aria-label')).toBe('SEAJ Capital, portfolio');
  });

  it('logs out through the shared header and returns to login', async () => {
    const { fixture, router, page } = await render('/dashboard');
    page.querySelector<HTMLButtonElement>('header button')!.click();
    await fixture.whenStable();
    expect(auth.logout).toHaveBeenCalledOnce();
    expect(router.url).toBe('/login');
    expect(page.querySelector('header')).toBeNull();
  });

  it('leaves public login outside the authenticated navigation', async () => {
    const { page } = await render('/login');
    expect(page.querySelector('header')).toBeNull();
    expect(page.textContent).toContain('Login');
  });

  it('redirects a signed-out visitor before showing a protected page', async () => {
    auth.isAuthenticated.mockReturnValue(false);
    const { router, page } = await render('/order');
    expect(router.url).toBe('/login');
    expect(page.querySelector('header')).toBeNull();
  });
});
