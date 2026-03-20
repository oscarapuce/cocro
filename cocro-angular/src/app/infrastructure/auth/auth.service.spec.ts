import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService } from './auth.service';
import { AuthResponse } from '@domain/models/auth.model';
import { environment } from '@infrastructure/environment';

describe('AuthService', () => {
  let service: AuthService;
  let httpTesting: HttpTestingController;

  const mockResponse: AuthResponse = {
    userId: 'user-1',
    username: 'testuser',
    roles: ['PLAYER'],
    token: 'jwt-token-123',
  };

  beforeEach(() => {
    localStorage.clear();

    TestBed.configureTestingModule({
      providers: [
        AuthService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });

    service = TestBed.inject(AuthService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
    localStorage.clear();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('login', () => {
    it('should make POST request and store session', () => {
      service.login({ username: 'test', password: 'pass' }).subscribe((res) => {
        expect(res).toEqual(mockResponse);
      });

      const req = httpTesting.expectOne(`${environment.apiBaseUrl}/auth/login`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ username: 'test', password: 'pass' });
      req.flush(mockResponse);

      expect(service.currentUser()).toEqual(mockResponse);
      expect(service.isAuthenticated()).toBe(true);
      expect(localStorage.getItem('cocro_token')).toBe('jwt-token-123');
      expect(JSON.parse(localStorage.getItem('cocro_user')!)).toEqual(mockResponse);
    });
  });

  describe('register', () => {
    it('should make POST request and store session', () => {
      service.register({ username: 'newuser', password: 'pass123' }).subscribe((res) => {
        expect(res).toEqual(mockResponse);
      });

      const req = httpTesting.expectOne(`${environment.apiBaseUrl}/auth/register`);
      expect(req.request.method).toBe('POST');
      req.flush(mockResponse);

      expect(service.currentUser()).toEqual(mockResponse);
      expect(service.isAuthenticated()).toBe(true);
    });
  });

  describe('createGuest', () => {
    it('should make POST request to /auth/guest', () => {
      const guestResponse: AuthResponse = {
        userId: 'guest-1',
        username: 'guest',
        roles: ['ANONYMOUS'],
        token: 'guest-token',
      };

      service.createGuest().subscribe((res) => {
        expect(res).toEqual(guestResponse);
      });

      const req = httpTesting.expectOne(`${environment.apiBaseUrl}/auth/guest`);
      expect(req.request.method).toBe('POST');
      req.flush(guestResponse);

      expect(service.isAnonymous()).toBe(true);
      expect(service.isPlayer()).toBe(false);
    });
  });

  describe('logout', () => {
    it('should clear localStorage and reset current user', () => {
      // First login
      service.login({ username: 'test', password: 'pass' }).subscribe();
      httpTesting.expectOne(`${environment.apiBaseUrl}/auth/login`).flush(mockResponse);

      expect(service.isAuthenticated()).toBe(true);

      // Then logout
      service.logout();
      expect(service.currentUser()).toBeNull();
      expect(service.isAuthenticated()).toBe(false);
      expect(localStorage.getItem('cocro_token')).toBeNull();
      expect(localStorage.getItem('cocro_user')).toBeNull();
    });
  });

  describe('token', () => {
    it('should return the token from localStorage', () => {
      localStorage.setItem('cocro_token', 'stored-token');
      expect(service.token()).toBe('stored-token');
    });

    it('should return null when no token stored', () => {
      expect(service.token()).toBeNull();
    });
  });

  describe('signal states', () => {
    it('isPlayer should be true for PLAYER role', () => {
      service.login({ username: 'test', password: 'pass' }).subscribe();
      httpTesting.expectOne(`${environment.apiBaseUrl}/auth/login`).flush(mockResponse);
      expect(service.isPlayer()).toBe(true);
    });

    it('isPlayer should be true for ADMIN role', () => {
      const adminResponse = { ...mockResponse, roles: ['ADMIN'] };
      service.login({ username: 'test', password: 'pass' }).subscribe();
      httpTesting.expectOne(`${environment.apiBaseUrl}/auth/login`).flush(adminResponse);
      expect(service.isPlayer()).toBe(true);
    });

    it('isAnonymous should be true for ANONYMOUS role', () => {
      const anonResponse = { ...mockResponse, roles: ['ANONYMOUS'] };
      service.login({ username: 'test', password: 'pass' }).subscribe();
      httpTesting.expectOne(`${environment.apiBaseUrl}/auth/login`).flush(anonResponse);
      expect(service.isAnonymous()).toBe(true);
      expect(service.isPlayer()).toBe(false);
    });
  });

  describe('loadStoredUser', () => {
    it('should load user from localStorage on construction', () => {
      localStorage.setItem('cocro_user', JSON.stringify(mockResponse));

      // Re-create service to trigger constructor
      const freshService = new AuthService(TestBed.inject(HttpClient));
      expect(freshService.currentUser()).toEqual(mockResponse);
      expect(freshService.isAuthenticated()).toBe(true);
    });

    it('should handle invalid JSON in localStorage', () => {
      localStorage.setItem('cocro_user', 'not-json');

      const freshService = new AuthService(TestBed.inject(HttpClient));
      expect(freshService.currentUser()).toBeNull();
    });
  });
});
