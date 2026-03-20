import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ToastService } from '@presentation/shared/components/toast/toast.service';
import { networkErrorInterceptor } from './network-error.interceptor';

describe('networkErrorInterceptor', () => {
  let http: HttpClient;
  let httpTesting: HttpTestingController;
  let toast: jasmine.SpyObj<ToastService>;

  beforeEach(() => {
    const toastSpy = {
      error: jest.fn(),
      success: jest.fn(),
      info: jest.fn(),
      warning: jest.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([networkErrorInterceptor])),
        provideHttpClientTesting(),
        { provide: ToastService, useValue: toastSpy },
      ],
    });

    http = TestBed.inject(HttpClient);
    httpTesting = TestBed.inject(HttpTestingController);
    toast = TestBed.inject(ToastService) as any;
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
    http.get('/api/grids').subscribe({
      error: (error) => {
        expect(error.message).toBe('Le serveur a rencontré une erreur.');
      },
    });

    const req = httpTesting.expectOne('/api/grids');
    req.flush({}, { status: 500, statusText: 'Server Error' });

    expect(toast.error).toHaveBeenCalledWith('Le serveur a rencontré une erreur.');
  });

  it('does not show a toast for a handled business error', () => {
    http.get('/api/grids').subscribe({
      error: (error) => {
        expect(error.message).toBe('Session introuvable.');
      },
    });

    const req = httpTesting.expectOne('/api/grids');
    req.flush(
      {
        errors: [{ code: 'SESSION_NOT_FOUND', message: 'Session not found' }],
      },
      { status: 404, statusText: 'Not Found' },
    );

    expect(toast.error).not.toHaveBeenCalled();
  });
});
