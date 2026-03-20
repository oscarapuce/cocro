import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { authGuard } from './auth.guard';
import { AuthService } from '@infrastructure/auth/auth.service';
import { signal } from '@angular/core';

describe('authGuard', () => {
  let mockRouter: { createUrlTree: jest.Mock };
  let mockAuth: { isAuthenticated: ReturnType<typeof signal<boolean>> };

  beforeEach(() => {
    mockRouter = { createUrlTree: jest.fn().mockReturnValue('login-url-tree' as unknown as UrlTree) };
    mockAuth = { isAuthenticated: signal(false) };

    TestBed.configureTestingModule({
      providers: [
        { provide: Router, useValue: mockRouter },
        { provide: AuthService, useValue: mockAuth },
      ],
    });
  });

  it('should return true when authenticated', () => {
    mockAuth.isAuthenticated.set(true);
    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as any, {} as any),
    );
    expect(result).toBe(true);
  });

  it('should redirect to /auth/login when not authenticated', () => {
    mockAuth.isAuthenticated.set(false);
    TestBed.runInInjectionContext(() =>
      authGuard({} as any, {} as any),
    );
    expect(mockRouter.createUrlTree).toHaveBeenCalledWith(['/auth/login']);
  });
});
