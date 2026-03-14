import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { authGuard } from './auth.guard';
import { AuthService } from '../services/auth.service';
import { signal } from '@angular/core';

describe('authGuard', () => {
  let routerSpy: jasmine.SpyObj<Router>;

  function setup(isAuthenticated: boolean) {
    const fakeAuth = {
      isAuthenticated: signal(isAuthenticated),
    } as unknown as AuthService;

    routerSpy = jasmine.createSpyObj<Router>('Router', ['createUrlTree']);

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: fakeAuth },
        { provide: Router, useValue: routerSpy },
      ],
    });
  }

  it('returns true when the user is authenticated', () => {
    setup(true);

    const result = TestBed.runInInjectionContext(() => authGuard({} as any, {} as any));

    expect(result).toBeTrue();
    expect(routerSpy.createUrlTree).not.toHaveBeenCalled();
  });

  it('returns a UrlTree redirecting to /auth/login when not authenticated', () => {
    setup(false);
    const fakeTree = {} as UrlTree;
    routerSpy.createUrlTree.and.returnValue(fakeTree);

    const result = TestBed.runInInjectionContext(() => authGuard({} as any, {} as any));

    expect(routerSpy.createUrlTree).toHaveBeenCalledWith(['/auth/login']);
    expect(result).toBe(fakeTree);
  });
});
