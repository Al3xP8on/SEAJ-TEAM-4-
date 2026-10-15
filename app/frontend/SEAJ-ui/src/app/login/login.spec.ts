// Tests Angular forms and the HttpClient adapter against the preserved two-factor API contract.
// Checks token storage, rotation, generic failures and cancellation through real component/service wiring.
import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { Router, provideRouter } from '@angular/router';
import { LoginComponent } from './login';
import { AuthService } from '../auth/auth.service';

const challenge = { challenge_id: '00000000-0000-4000-8000-000000000001', expires_in: 300, message: 'Enter your code' };
const pair = (refresh = 'refresh-1') => ({
  access_token: `${btoa(JSON.stringify({ alg: 'HS256' }))}.${btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 120, amr: ['pwd', 'email_otp'] }))}.signature`.replace(/=/g, ''),
  refresh_token: refresh, expires_in: 120, token_type: 'Bearer',
});
describe('Angular email authentication', () => {
  let router: Router;
  let fixture: ComponentFixture<LoginComponent>;
  let component: LoginComponent;
  let auth: AuthService;
  let http: HttpTestingController;
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ imports: [LoginComponent], providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });
  afterEach(() => { auth.logout(); fixture.destroy(); http.verify(); sessionStorage.clear(); });
  async function startLogin() {
    const pending = firstValueFrom(auth.login('alice', 'password'));
    const request = http.expectOne('/auth/login');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ username: 'alice', password: 'password' });
    request.flush(challenge, { status: 202, statusText: 'Accepted' });
    await pending;
  }
  it('requires email verification before storing either token', async () => {
    await startLogin();
    expect(auth.getToken()).toBeNull(); expect(sessionStorage.getItem('seaj.refresh_token')).toBeNull();
    const pending = firstValueFrom(auth.verifyLogin('123456'));
    const request = http.expectOne('/auth/login/verify');
    expect(request.request.body).toEqual({ challenge_id: challenge.challenge_id, code: '123456' });
    request.flush(pair()); await pending;
    expect(auth.isAuthenticated()).toBe(true); expect(sessionStorage.getItem('seaj.refresh_token')).toBe('refresh-1');
  });
  it('rotates tokens through HttpClient and prompts login when refresh is rejected', async () => {
    await startLogin();
    const verified = firstValueFrom(auth.verifyLogin('123456'));
    http.expectOne('/auth/login/verify').flush(pair()); await verified;
    const renewed = firstValueFrom(auth.refresh());
    const request = http.expectOne('/auth/refresh');
    expect(request.request.body).toEqual({ refresh_token: 'refresh-1' });
    request.flush(pair('refresh-2')); await renewed;
    expect(sessionStorage.getItem('seaj.refresh_token')).toBe('refresh-2');
    const rejected = firstValueFrom(auth.refresh());
    const assertion = expect(rejected).rejects.toThrow('Invalid or expired refresh token');
    http.expectOne('/auth/refresh').flush({ message: 'Invalid or expired refresh token' }, { status: 401, statusText: 'Unauthorized' });
    await assertion;
    expect(auth.getToken()).toBeNull(); expect(sessionStorage.getItem('seaj.refresh_token')).toBeNull();
    fixture.detectChanges();
    expect(component.loginMessage()).toContain('session has expired');
  });
  it('rejects an old direct-token response that would bypass the email factor', async () => {
    const pending = firstValueFrom(auth.login('alice', 'password'));
    const assertion = expect(pending).rejects.toThrow('invalid verification challenge');
    http.expectOne('/auth/login').flush(pair()); await assertion;
    expect(auth.getToken()).toBeNull();
  });
  it('sends an optional phone number with the registration when one is entered', () => {
    component.showView('register'); component.regUsername = 'alice'; component.regPassword = 'SecurePass123!';
    component.regEmail = 'alice@example.com'; component.regPhone = ' +353-85-456-7891 ';
    component.onRegister();
    expect(http.expectOne('/auth/register').request.body).toEqual({
      username: 'alice', password: 'SecurePass123!', email: 'alice@example.com', phone: '+353-85-456-7891',
    });
  });
  it('blocks registration with an invalid phone number', () => {
    component.showView('register'); component.regUsername = 'alice'; component.regPassword = 'SecurePass123!';
    component.regEmail = 'alice@example.com'; component.regPhone = 'call me';
    component.onRegister();
    http.expectNone('/auth/register');
    expect(component.registerMessage()).toContain('valid phone number');
    expect(component.registerMessageType()).toBe('error');
  });
  it('requires email at signup and shows account creation only after verification', async () => {
    component.showView('register'); component.regUsername = 'alice'; component.regPassword = 'SecurePass123!';
    component.onRegister(); http.expectNone('/auth/register');
    component.regEmail = 'alice@example.com'; component.onRegister();
    const request = http.expectOne('/auth/register');
    expect(request.request.body).toEqual({ username: 'alice', password: 'SecurePass123!', email: 'alice@example.com' });
    request.flush(challenge, { status: 202, statusText: 'Accepted' });
    await vi.waitFor(() => expect(component.view()).toBe('verify'));
    fixture.detectChanges(); expect(fixture.nativeElement.querySelector('#verificationCode')).toBeTruthy();
    expect(component.regPassword).toBe(''); expect(auth.getToken()).toBeNull();
    component.code = '123456'; component.onVerify();
    http.expectOne('/auth/register/verify').flush({ username: 'alice', account_id: 'ACC-12AB34CD', email_verified: true }, { status: 201, statusText: 'Created' });
    await vi.waitFor(() => expect(component.loginMessage()).toContain('ACC-12AB34CD'));
    expect(component.view()).toBe('login'); expect(auth.getToken()).toBeNull();
  });
  it('shows the login code form, permits a wrong-code retry and never displays tokens', async () => {
    component.username = 'alice'; component.password = 'password'; component.onLogin();
    http.expectOne('/auth/login').flush(challenge, { status: 202, statusText: 'Accepted' });
    await vi.waitFor(() => expect(component.view()).toBe('verify'));
    expect(component.password).toBe('');
    component.code = '000000'; component.onVerify();
    http.expectOne('/auth/login/verify').flush({ message: 'Invalid or expired verification code' }, { status: 401, statusText: 'Unauthorized' });
    await vi.waitFor(() => expect(component.verificationMessage()).toContain('Invalid or expired'));
    expect(auth.getToken()).toBeNull();
    component.code = '123456'; component.onVerify();
    const tokens = pair(); http.expectOne('/auth/login/verify').flush(tokens);
    await vi.waitFor(() => expect(component.loginMessage()).toBe('Login successful.'));
    expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
    fixture.detectChanges(); expect(fixture.nativeElement.textContent).not.toContain(tokens.access_token);
  });
  it('keeps cancelled verification from restoring a session or updating the view', async () => {
    component.username = 'alice'; component.password = 'password'; component.onLogin();
    http.expectOne('/auth/login').flush(challenge, { status: 202, statusText: 'Accepted' });
    await vi.waitFor(() => expect(component.view()).toBe('verify'));
    component.code = '123456'; component.onVerify();
    const pending = http.expectOne('/auth/login/verify'); component.cancelVerification(); pending.flush(pair());
    await vi.waitFor(() => expect(component.verificationLoading()).toBe(false));
    expect(component.view()).toBe('login'); expect(auth.getToken()).toBeNull();
    expect(component.loginMessage()).not.toBe('Login successful.');
    expect(router.navigate).not.toHaveBeenCalled();
  });
  it('displays the generic credentials error without storing tokens', async () => {
    component.username = 'unknown'; component.password = 'password'; component.onLogin();
    http.expectOne('/auth/login').flush({ message: 'Invalid credentials' }, { status: 401, statusText: 'Unauthorized' });
    await vi.waitFor(() => expect(component.loginMessage()).toBe('Invalid credentials'));
    expect(component.loginLoading()).toBe(false); expect(auth.getToken()).toBeNull();
  });
});
