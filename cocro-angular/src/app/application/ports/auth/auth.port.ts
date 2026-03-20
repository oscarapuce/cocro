import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';
import { AuthResponse, LoginRequest, RegisterRequest } from '@domain/models/auth.model';

export interface AuthPort {
  login(request: LoginRequest): Observable<AuthResponse>;
  register(request: RegisterRequest): Observable<AuthResponse>;
  createGuest(): Observable<AuthResponse>;
}

export const AUTH_PORT = new InjectionToken<AuthPort>('AUTH_PORT');
