import { TestBed } from '@angular/core/testing';
import { Router, UrlTree, provideRouter } from '@angular/router';
import { AuthService } from './auth.service';
import { authGuard } from './auth.guard';

describe('authGuard', () => {
  const auth = { isAuthenticated: vi.fn() };
  const runGuard = () =>
    TestBed.runInInjectionContext(() => authGuard({} as never, {} as never));

  beforeEach(() => {
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
});
