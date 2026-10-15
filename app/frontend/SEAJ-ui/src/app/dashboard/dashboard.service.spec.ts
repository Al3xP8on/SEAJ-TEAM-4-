import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { DashboardService, accountIdFromToken } from './dashboard.service';

const tokenFor = (claims: object) =>
  `${btoa(JSON.stringify({ alg: 'HS256' }))}.${btoa(JSON.stringify(claims)).replace(/=+$/, '')}.signature`;

describe('DashboardService', () => {
  const auth = { getToken: vi.fn<() => string | null>() };
  let service: DashboardService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: auth },
      ],
    });
    service = TestBed.inject(DashboardService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the logged-in account, then its positions and orders', async () => {
    auth.getToken.mockReturnValue(tokenFor({ sub: '7', username: 'alice' }));

    const result = firstValueFrom(service.load());
    http.expectOne('/api/v1/accounts/7').flush({ id: 7, accountId: 'ACC-0007' });
    http.expectOne('/api/v1/accounts/7/positions').flush([{ symbol: 'AAPL' }]);
    // Orders are looked up by the ACC- id from the account, not the token's numeric id
    http.expectOne('/api/v1/orders/account/ACC-0007').flush([{ orderId: 'o1' }]);

    expect(await result).toEqual({
      account: { id: 7, accountId: 'ACC-0007' },
      positions: [{ symbol: 'AAPL' }],
      orders: [{ orderId: 'o1' }],
    });
  });

  it('fails without calling the API when there is no session', async () => {
    auth.getToken.mockReturnValue(null);

    await expect(firstValueFrom(service.load())).rejects.toThrow('No active session');
    http.expectNone(() => true);
  });

  it('reads the subject from a token and rejects malformed ones', () => {
    expect(accountIdFromToken(tokenFor({ sub: '42' }))).toBe('42');
    expect(accountIdFromToken(tokenFor({ username: 'no-sub' }))).toBeNull();
    expect(accountIdFromToken('not-a-jwt')).toBeNull();
    expect(accountIdFromToken(null)).toBeNull();
  });
});
