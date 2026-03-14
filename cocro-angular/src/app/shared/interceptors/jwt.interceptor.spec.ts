import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import {
  provideHttpClient,
  withInterceptors,
  HttpClient,
} from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { jwtInterceptor } from './jwt.interceptor';
import { AuthService } from '../services/auth.service';

const FAKE_TOKEN = 'test-jwt-token-xyz';

describe('jwtInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  function setupWithToken(token: string | null): void {
    authServiceSpy = jasmine.createSpyObj<AuthService>('AuthService', ['token']);
    authServiceSpy.token.and.returnValue(token);

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([jwtInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authServiceSpy },
      ],
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  }

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  // ─── Token present ──────────────────────────────────────────────────────────

  describe('when a token is present', () => {
    beforeEach(() => setupWithToken(FAKE_TOKEN));

    it('adds Authorization header for requests to /api/', () => {
      http.get('http://localhost:8080/api/sessions').subscribe();

      const req = httpMock.expectOne('http://localhost:8080/api/sessions');
      expect(req.request.headers.get('Authorization')).toBe(`Bearer ${FAKE_TOKEN}`);
      req.flush({});
    });

    it('adds Authorization header for requests to /auth/login', () => {
      http.post('http://localhost:8080/auth/login', {}).subscribe();

      const req = httpMock.expectOne('http://localhost:8080/auth/login');
      expect(req.request.headers.get('Authorization')).toBe(`Bearer ${FAKE_TOKEN}`);
      req.flush({});
    });

    it('does NOT add Authorization header for /auth/register', () => {
      http.post('http://localhost:8080/auth/register', {}).subscribe();

      const req = httpMock.expectOne('http://localhost:8080/auth/register');
      expect(req.request.headers.get('Authorization')).toBeNull();
      req.flush({});
    });

    it('does NOT add Authorization header for /auth/guest', () => {
      http.post('http://localhost:8080/auth/guest', {}).subscribe();

      const req = httpMock.expectOne('http://localhost:8080/auth/guest');
      expect(req.request.headers.get('Authorization')).toBeNull();
      req.flush({});
    });
  });

  // ─── No token ───────────────────────────────────────────────────────────────

  describe('when no token is present', () => {
    beforeEach(() => setupWithToken(null));

    it('does NOT add Authorization header for /api/ requests', () => {
      http.get('http://localhost:8080/api/sessions').subscribe();

      const req = httpMock.expectOne('http://localhost:8080/api/sessions');
      expect(req.request.headers.get('Authorization')).toBeNull();
      req.flush({});
    });

    it('does NOT add Authorization header for /auth/login', () => {
      http.post('http://localhost:8080/auth/login', {}).subscribe();

      const req = httpMock.expectOne('http://localhost:8080/auth/login');
      expect(req.request.headers.get('Authorization')).toBeNull();
      req.flush({});
    });
  });
});
