import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { jwtInterceptor } from './jwt.interceptor';
import { AuthService } from './auth.service';

describe('jwtInterceptor', () => {
  let http: HttpClient;
  let httpTesting: HttpTestingController;
  let authService: jasmine.SpyObj<AuthService>;

  beforeEach(() => {
    const authSpy = {
      token: jest.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([jwtInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authSpy },
      ],
    });

    http = TestBed.inject(HttpClient);
    httpTesting = TestBed.inject(HttpTestingController);
    authService = TestBed.inject(AuthService) as any;
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should add Authorization header for /api/ URLs when token exists', () => {
    authService.token.mockReturnValue('my-jwt-token');

    http.get('/api/grids').subscribe();

    const req = httpTesting.expectOne('/api/grids');
    expect(req.request.headers.get('Authorization')).toBe('Bearer my-jwt-token');
    req.flush({});
  });

  it('should add Authorization header for /auth/login URL when token exists', () => {
    authService.token.mockReturnValue('my-jwt-token');

    http.post('/auth/login', {}).subscribe();

    const req = httpTesting.expectOne('/auth/login');
    expect(req.request.headers.get('Authorization')).toBe('Bearer my-jwt-token');
    req.flush({});
  });

  it('should NOT add Authorization header when no token', () => {
    authService.token.mockReturnValue(null);

    http.get('/api/grids').subscribe();

    const req = httpTesting.expectOne('/api/grids');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('should NOT add Authorization header for non-matching URLs', () => {
    authService.token.mockReturnValue('my-jwt-token');

    http.get('/other/endpoint').subscribe();

    const req = httpTesting.expectOne('/other/endpoint');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });
});
