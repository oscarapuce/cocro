import { Injectable, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { tap } from 'rxjs/operators';
import { Observable } from 'rxjs';
import { environment } from '@infrastructure/environment';
import { AuthResponse, LoginRequest, RegisterRequest } from '@domain/models/auth.model';
import { AuthPort } from '@application/ports/auth/auth.port';

const TOKEN_KEY = 'cocro_token';
const USER_KEY = 'cocro_user';

@Injectable({ providedIn: 'root' })
export class AuthService implements AuthPort {
  private readonly _currentUser = signal<AuthResponse | null>(this.loadStoredUser());
  readonly currentUser = this._currentUser.asReadonly();
  readonly isAuthenticated = computed(() => this._currentUser() !== null);
  readonly isAnonymous = computed(() =>
    this._currentUser()?.roles.includes('ANONYMOUS') ?? false
  );
  readonly isPlayer = computed(() =>
    this._currentUser()?.roles.some((r) => r === 'PLAYER' || r === 'ADMIN') ?? false
  );

  constructor(private http: HttpClient) {}

  login(req: LoginRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${environment.apiBaseUrl}/auth/login`, req)
      .pipe(tap((res) => this.storeSession(res)));
  }

  register(req: RegisterRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${environment.apiBaseUrl}/auth/register`, req)
      .pipe(tap((res) => this.storeSession(res)));
  }

  createGuest(): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${environment.apiBaseUrl}/auth/guest`, {})
      .pipe(tap((res) => this.storeSession(res)));
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    this._currentUser.set(null);
  }

  token(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  private storeSession(res: AuthResponse): void {
    localStorage.setItem(TOKEN_KEY, res.token);
    localStorage.setItem(USER_KEY, JSON.stringify(res));
    this._currentUser.set(res);
  }

  private loadStoredUser(): AuthResponse | null {
    try {
      const raw = localStorage.getItem(USER_KEY);
      return raw ? (JSON.parse(raw) as AuthResponse) : null;
    } catch {
      return null;
    }
  }
}
