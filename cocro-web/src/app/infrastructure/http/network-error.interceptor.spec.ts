import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { ToastService } from '@presentation/shared/components/toast/toast.service';
import { AuthService } from '@infrastructure/auth/auth.service';
import { networkErrorInterceptor } from './network-error.interceptor';

describe('networkErrorInterceptor', () => {
  let http: HttpClient;
  let httpTesting: HttpTestingController;
  let toast: jest.Mocked<Pick<ToastService, 'error' | 'success' | 'info' | 'warning'>>;
  let auth: jest.Mocked<Pick<AuthService, 'logout'>>;
  let router: jest.Mocked<Pick<Router, 'navigate'>>;

  beforeEach(() => {
    toast = { error: jest.fn(), success: jest.fn(), info: jest.fn(), warning: jest.fn() };
    auth = { logout: jest.fn() };
    router = { navigate: jest.fn() };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([networkErrorInterceptor])),
        provideHttpClientTesting(),
        { provide: ToastService, useValue: toast },
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
      ],
    });

    http = TestBed.inject(HttpClient);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('shows a toast for an unreachable server', () => {
    http.get('/api/grids').subscribe({
      error: (error) => {
        expect(error.message).toBe('Impossible de contacter le serveur.');
      },
    });

    const req = httpTesting.expectOne('/api/grids');
    req.error(new ProgressEvent('error'), { status: 0 });

    expect(toast.error).toHaveBeenCalledWith('Impossible de contacter le serveur.');
  });

  it('shows a toast for a server failure', () => {
    http.get('/api/grids').subscribe({ error: () => {} });

    const req = httpTesting.expectOne('/api/grids');
    req.flush({}, { status: 500, statusText: 'Server Error' });

    expect(toast.error).toHaveBeenCalledWith('Le serveur a rencontré une erreur.');
  });

  it('does not show a toast for a handled business error', () => {
    http.get('/api/grids').subscribe({ error: () => {} });

    const req = httpTesting.expectOne('/api/grids');
    req.flush(
      { errors: [{ code: 'SESSION_NOT_FOUND', message: 'Session not found' }] },
      { status: 404, statusText: 'Not Found' },
    );

    expect(toast.error).not.toHaveBeenCalled();
  });

  it('logs out and redirects to login on 401 for non-auth endpoints', () => {
    http.get('/api/sessions/ABC/state').subscribe({ error: () => {} });

    const req = httpTesting.expectOne('/api/sessions/ABC/state');
    req.flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(auth.logout).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/auth/login']);
    expect(toast.error).not.toHaveBeenCalled();
  });

  it('does not redirect on 401 for auth endpoints (bad credentials)', () => {
    http.post('/auth/login', {}).subscribe({ error: () => {} });

    const req = httpTesting.expectOne('/auth/login');
    req.flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(auth.logout).not.toHaveBeenCalled();
    expect(router.navigate).not.toHaveBeenCalled();
  });
});
