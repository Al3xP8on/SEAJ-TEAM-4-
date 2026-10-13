import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService } from './auth.service';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  const auth = { getToken: vi.fn<() => string | null>() };
  let http: HttpClient;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: auth },
      ],
    });
    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controller.verify());

  it('adds the bearer token to API requests', () => {
    auth.getToken.mockReturnValue('abc.def.ghi');

    http.get('/api/v1/accounts/1').subscribe();

    expect(controller.expectOne('/api/v1/accounts/1').request.headers.get('Authorization')).toBe('Bearer abc.def.ghi');
  });

  it('leaves auth-service and other requests alone', () => {
    auth.getToken.mockReturnValue('abc.def.ghi');

    http.post('/auth/refresh', {}).subscribe();

    expect(controller.expectOne('/auth/refresh').request.headers.has('Authorization')).toBe(false);
  });

  it('sends API requests without a header when there is no session', () => {
    auth.getToken.mockReturnValue(null);

    http.get('/api/v1/accounts/1').subscribe();

    expect(controller.expectOne('/api/v1/accounts/1').request.headers.has('Authorization')).toBe(false);
  });
});
