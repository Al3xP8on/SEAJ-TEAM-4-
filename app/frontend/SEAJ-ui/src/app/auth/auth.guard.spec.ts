import { TestBed } from '@angular/core/testing';
import { Router, UrlTree, provideRouter } from '@angular/router';
import { AuthService } from './auth.service';
import { adminGuard, authGuard, dashboardGuard, hasAdminRole } from './auth.guard';

describe('authGuard', () => {
  const auth = { isAuthenticated: vi.fn(), getToken: vi.fn() };
  const runGuard = () => TestBed.runInInjectionContext(() => authGuard({} as never, {} as never));

  beforeEach(() => {
    auth.isAuthenticated.mockReset().mockReturnValue(true);
    auth.getToken.mockReset().mockReturnValue(null);
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
    });
  });

  it('opens the route for a logged-in user', () => {
    auth.isAuthenticated.mockReturnValue(true);

    expect(runGuard()).toBe(true);
  });

  it('sends anyone without a session to /login', () => {
    auth.isAuthenticated.mockReturnValue(false);

    const result = runGuard() as UrlTree;

    expect(TestBed.inject(Router).serializeUrl(result)).toBe('/login');
  });

  it('does not infer an admin role from missing, malformed or non-array claims', () => {
    for (const token of [null, 'broken', 'h.invalid.s', `h.${btoa('{"roles":"ADMIN"}')}.s`]) {
      expect(hasAdminRole(token)).toBe(false);
    }
  });

  it('allows admins and redirects normal users away from administration', () => {
    const run = () => TestBed.runInInjectionContext(() => adminGuard({} as never, {} as never));
    auth.getToken.mockReturnValue(`h.${btoa('{"roles":["ADMIN","USER"]}')}.s`);
    expect(run()).toBe(true);
    auth.getToken.mockReturnValue(`h.${btoa('{"roles":["USER"]}')}.s`);
    expect(TestBed.inject(Router).serializeUrl(run() as UrlTree)).toBe('/dashboard');
    auth.isAuthenticated.mockReturnValue(false);
    expect(TestBed.inject(Router).serializeUrl(run() as UrlTree)).toBe('/login');
  });

  it('opens a different dashboard for admins while keeping the normal overview', () => {
    const run = () => TestBed.runInInjectionContext(() => dashboardGuard({} as never, {} as never));
    expect(run()).toBe(true);
    auth.getToken.mockReturnValue(`h.${btoa('{"roles":["ADMIN"]}')}.s`);
    expect(TestBed.inject(Router).serializeUrl(run() as UrlTree)).toBe('/admin');
  });
});
