import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AdminService } from './admin.service';

describe('AdminService', () => {
  let service: AdminService;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AdminService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('loads only the safe admin account endpoint', () => {
    service.accounts().subscribe();
    http.expectOne('/api/v1/admin/accounts').flush([]);
  });

  it('sends status changes without replacing account data', () => {
    service.changeStatus(2, 'SUSPENDED').subscribe();
    const request = http.expectOne('/api/v1/admin/accounts/2/status');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ status: 'SUSPENDED' });
    request.flush({});
  });

  it('loads the selected account holdings and complete order history', () => {
    service
      .details('ACC-0002')
      .subscribe((details) => expect(details).toEqual({ positions: [], orders: [] }));
    http.expectOne('/api/v1/accounts/ACC-0002/positions').flush([]);
    http.expectOne('/api/v1/orders/account/ACC-0002').flush([]);
  });
});
