import { TestBed } from '@angular/core/testing';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { AuthService } from './auth.service';
import { AuthResponse, LoginRequest, RegisterRequest } from '../models/auth.models';

const BASE = 'http://localhost:8080';
const TOKEN_KEY = 'cocro_token';
const USER_KEY = 'cocro_user';

const mockUser: AuthResponse = {
  userId: 'user-1',
  username: 'alice',
  roles: ['PLAYER'],
  token: 'jwt-token-abc',
};

const mockAnonymousUser: AuthResponse = {
  userId: 'anon-1',
  username: 'guest_xyz',
  roles: ['ANONYMOUS'],
  token: 'anon-jwt-token',
};

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), AuthService],
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  // ─── login() ───────────────────────────────────────────────────────────────

  describe('login()', () => {
    it('makes a POST to /auth/login with the request body', () => {
      const req: LoginRequest = { username: 'alice', password: 'pass123' };
      service.login(req).subscribe();

      const testReq = httpMock.expectOne(`${BASE}/auth/login`);
      expect(testReq.request.method).toBe('POST');
      expect(testReq.request.body).toEqual(req);
      testReq.flush(mockUser);
    });

    it('stores the token in localStorage after successful login', () => {
      service.login({ username: 'alice', password: 'pass123' }).subscribe();
      httpMock.expectOne(`${BASE}/auth/login`).flush(mockUser);

      expect(localStorage.getItem(TOKEN_KEY)).toBe(mockUser.token);
    });

    it('stores the user JSON in localStorage after successful login', () => {
      service.login({ username: 'alice', password: 'pass123' }).subscribe();
      httpMock.expectOne(`${BASE}/auth/login`).flush(mockUser);

      const stored = JSON.parse(localStorage.getItem(USER_KEY)!);
      expect(stored).toEqual(mockUser);
    });

    it('updates the currentUser signal after successful login', () => {
      expect(service.currentUser()).toBeNull();

      service.login({ username: 'alice', password: 'pass123' }).subscribe();
      httpMock.expectOne(`${BASE}/auth/login`).flush(mockUser);

      expect(service.currentUser()).toEqual(mockUser);
    });
  });

  // ─── register() ────────────────────────────────────────────────────────────

  describe('register()', () => {
    it('makes a POST to /auth/register with the request body', () => {
      const req: RegisterRequest = { username: 'bob', password: 'secret', email: 'bob@example.com' };
      service.register(req).subscribe();

      const testReq = httpMock.expectOne(`${BASE}/auth/register`);
      expect(testReq.request.method).toBe('POST');
      expect(testReq.request.body).toEqual(req);
      testReq.flush(mockUser);
    });

    it('stores session after successful register', () => {
      service.register({ username: 'bob', password: 'secret' }).subscribe();
      httpMock.expectOne(`${BASE}/auth/register`).flush(mockUser);

      expect(localStorage.getItem(TOKEN_KEY)).toBe(mockUser.token);
      expect(service.currentUser()).toEqual(mockUser);
    });
  });

  // ─── createGuest() ─────────────────────────────────────────────────────────

  describe('createGuest()', () => {
    it('makes a POST to /auth/guest with an empty body', () => {
      service.createGuest().subscribe();

      const testReq = httpMock.expectOne(`${BASE}/auth/guest`);
      expect(testReq.request.method).toBe('POST');
      expect(testReq.request.body).toEqual({});
      testReq.flush(mockAnonymousUser);
    });

    it('stores session with ANONYMOUS role after createGuest', () => {
      service.createGuest().subscribe();
      httpMock.expectOne(`${BASE}/auth/guest`).flush(mockAnonymousUser);

      expect(localStorage.getItem(TOKEN_KEY)).toBe(mockAnonymousUser.token);
      const stored: AuthResponse = JSON.parse(localStorage.getItem(USER_KEY)!);
      expect(stored.roles).toContain('ANONYMOUS');
      expect(service.currentUser()).toEqual(mockAnonymousUser);
    });
  });

  // ─── logout() ──────────────────────────────────────────────────────────────

  describe('logout()', () => {
    it('clears token and user from localStorage', () => {
      localStorage.setItem(TOKEN_KEY, mockUser.token);
      localStorage.setItem(USER_KEY, JSON.stringify(mockUser));

      service.logout();

      expect(localStorage.getItem(TOKEN_KEY)).toBeNull();
      expect(localStorage.getItem(USER_KEY)).toBeNull();
    });

    it('sets currentUser signal to null', () => {
      service.login({ username: 'alice', password: 'pass' }).subscribe();
      httpMock.expectOne(`${BASE}/auth/login`).flush(mockUser);
      expect(service.currentUser()).not.toBeNull();

      service.logout();
      expect(service.currentUser()).toBeNull();
    });
  });

  // ─── isAuthenticated() ─────────────────────────────────────────────────────

  describe('isAuthenticated()', () => {
    it('is true when a user is stored in the signal', () => {
      service.login({ username: 'alice', password: 'pass' }).subscribe();
      httpMock.expectOne(`${BASE}/auth/login`).flush(mockUser);

      expect(service.isAuthenticated()).toBeTrue();
    });

    it('is false after logout', () => {
      service.login({ username: 'alice', password: 'pass' }).subscribe();
      httpMock.expectOne(`${BASE}/auth/login`).flush(mockUser);

      service.logout();
      expect(service.isAuthenticated()).toBeFalse();
    });

    it('is false on a fresh service with no stored user', () => {
      expect(service.isAuthenticated()).toBeFalse();
    });
  });

  // ─── isAnonymous() ─────────────────────────────────────────────────────────

  describe('isAnonymous()', () => {
    it('is true when the current user has the ANONYMOUS role', () => {
      service.createGuest().subscribe();
      httpMock.expectOne(`${BASE}/auth/guest`).flush(mockAnonymousUser);

      expect(service.isAnonymous()).toBeTrue();
    });

    it('is false when the current user is a PLAYER', () => {
      service.login({ username: 'alice', password: 'pass' }).subscribe();
      httpMock.expectOne(`${BASE}/auth/login`).flush(mockUser);

      expect(service.isAnonymous()).toBeFalse();
    });

    it('is false when no user is logged in', () => {
      expect(service.isAnonymous()).toBeFalse();
    });
  });

  // ─── isPlayer() ────────────────────────────────────────────────────────────

  describe('isPlayer()', () => {
    it('is true when the current user has the PLAYER role', () => {
      service.login({ username: 'alice', password: 'pass' }).subscribe();
      httpMock.expectOne(`${BASE}/auth/login`).flush(mockUser);

      expect(service.isPlayer()).toBeTrue();
    });

    it('is true when the current user has the ADMIN role', () => {
      const adminUser: AuthResponse = { ...mockUser, roles: ['ADMIN'] };
      service.login({ username: 'alice', password: 'pass' }).subscribe();
      httpMock.expectOne(`${BASE}/auth/login`).flush(adminUser);

      expect(service.isPlayer()).toBeTrue();
    });

    it('is false for an ANONYMOUS user', () => {
      service.createGuest().subscribe();
      httpMock.expectOne(`${BASE}/auth/guest`).flush(mockAnonymousUser);

      expect(service.isPlayer()).toBeFalse();
    });

    it('is false when no user is logged in', () => {
      expect(service.isPlayer()).toBeFalse();
    });
  });

  // ─── token() ───────────────────────────────────────────────────────────────

  describe('token()', () => {
    it('returns the stored token from localStorage', () => {
      localStorage.setItem(TOKEN_KEY, 'stored-jwt');
      expect(service.token()).toBe('stored-jwt');
    });

    it('returns null when no token is stored', () => {
      expect(service.token()).toBeNull();
    });

    it('returns null after logout', () => {
      localStorage.setItem(TOKEN_KEY, 'stored-jwt');
      service.logout();
      expect(service.token()).toBeNull();
    });
  });

  // ─── loadStoredUser() ──────────────────────────────────────────────────────

  describe('loadStoredUser() (via service initialisation)', () => {
    it('reads the current user from localStorage on init when valid JSON is present', () => {
      // Pre-seed storage before creating a fresh service
      localStorage.setItem(USER_KEY, JSON.stringify(mockUser));
      localStorage.setItem(TOKEN_KEY, mockUser.token);

      // Recreate TestBed so the new AuthService instance calls loadStoredUser() with pre-seeded data
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        providers: [provideHttpClient(), provideHttpClientTesting(), AuthService],
      });
      const freshService = TestBed.inject(AuthService);
      // Re-assign httpMock so afterEach verify() works
      httpMock = TestBed.inject(HttpTestingController);

      expect(freshService.currentUser()).toEqual(mockUser);
    });

    it('does not throw when localStorage contains malformed JSON', () => {
      localStorage.setItem(USER_KEY, 'not-valid-json{{{');

      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        providers: [provideHttpClient(), provideHttpClientTesting(), AuthService],
      });
      const freshService = TestBed.inject(AuthService);
      httpMock = TestBed.inject(HttpTestingController);

      expect(freshService.currentUser()).toBeNull();
    });

    it('currentUser signal is null on init when no user is in localStorage', () => {
      expect(service.currentUser()).toBeNull();
    });
  });
});
