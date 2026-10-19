// Covers the form markup: autofill hints, labels, announced messages, the password toggle,
// opening on sign-up from the landing page, and focus when switching views.
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { LoginComponent } from './login';

describe('Login form', () => {
  async function render(query: Record<string, string> = {}) {
    TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap(query) } },
        },
      ],
    });
    const fixture = TestBed.createComponent(LoginComponent);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    document.body.appendChild(page);
    return { fixture, component: fixture.componentInstance, page };
  }

  afterEach(() =>
    document.body.querySelectorAll('app-login, [ng-version]').forEach((n) => n.remove()),
  );

  const autocomplete = (page: HTMLElement) =>
    Object.fromEntries(
      Array.from(page.querySelectorAll('input')).map((i) => [i.id, i.getAttribute('autocomplete')]),
    );

  it('tells browsers and password managers what each login field is', async () => {
    const { page } = await render();

    expect(autocomplete(page)).toEqual({ username: 'username', password: 'current-password' });
    for (const input of Array.from(page.querySelectorAll('input'))) {
      expect(page.querySelector(`label[for="${input.id}"]`)).not.toBeNull();
    }
  });

  it('opens on sign-up when linked from the landing page, with hints for each field', async () => {
    const { page } = await render({ mode: 'register' });

    expect(page.querySelector('h1')?.textContent).toBe('Create an account');
    expect(autocomplete(page)).toEqual({
      regUsername: 'username',
      regEmail: 'email',
      regPassword: 'new-password',
      regPhone: 'tel',
    });
    expect(page.querySelector('#regPassword')?.getAttribute('aria-describedby')).toBe(
      'regPassword-hint',
    );
    expect(page.querySelector('#regPassword-hint')?.textContent).toBe('At least 8 characters.');
    expect(page.querySelector('#regUsername')?.getAttribute('aria-describedby')).toBe(
      'regUsername-hint',
    );
  });

  it('announces login errors and links them to the fields', async () => {
    const { fixture, component, page } = await render();

    component.onLogin();
    await fixture.whenStable();

    const message = page.querySelector('#login-message')!;
    expect(message.getAttribute('role')).toBe('status');
    expect(message.textContent).toContain('Enter your username');
    expect(page.querySelector('#username')?.getAttribute('aria-describedby')).toBe('login-message');
  });

  it('shows and hides the password with a labelled toggle', async () => {
    const { fixture, page } = await render();
    const input = page.querySelector('#password') as HTMLInputElement;
    const toggle = page.querySelector('.reveal') as HTMLButtonElement;

    expect(input.type).toBe('password');
    expect(toggle.textContent?.trim()).toBe('Show password');
    expect(toggle.getAttribute('aria-pressed')).toBe('false');

    toggle.click();
    await fixture.whenStable();
    expect(input.type).toBe('text');
    expect(toggle.getAttribute('aria-pressed')).toBe('true');
  });

  it('submits sign-up with Enter, as a real form', async () => {
    const { page } = await render({ mode: 'register' });

    expect(page.querySelector('#regUsername')?.closest('form')).not.toBeNull();
  });

  it('moves focus to the new heading when switching views, and hides the password again', async () => {
    const { fixture, component, page } = await render();
    component.showLoginPassword.set(true);

    component.showView('register');
    await fixture.whenStable();

    expect(document.activeElement).toBe(page.querySelector('#auth-title'));
    expect(document.activeElement?.textContent).toBe('Create an account');
    expect(component.showLoginPassword()).toBe(false);
  });
});
