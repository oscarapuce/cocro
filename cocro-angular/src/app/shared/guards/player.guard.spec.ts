import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { playerGuard } from './player.guard';
import { AuthService } from '../services/auth.service';
import { signal } from '@angular/core';

describe('playerGuard', () => {
  let routerSpy: jasmine.SpyObj<Router>;

  function setup(isPlayer: boolean, isAnonymous: boolean) {
    const fakeAuth = {
      isPlayer: signal(isPlayer),
      isAnonymous: signal(isAnonymous),
    } as unknown as AuthService;

    routerSpy = jasmine.createSpyObj<Router>('Router', ['createUrlTree']);

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: fakeAuth },
        { provide: Router, useValue: routerSpy },
      ],
    });
  }

  it('returns true when isPlayer() is true', () => {
    setup(true, false);

    const result = TestBed.runInInjectionContext(() => playerGuard({} as any, {} as any));

    expect(result).toBeTrue();
    expect(routerSpy.createUrlTree).not.toHaveBeenCalled();
  });

  it('redirects to "/" when the user is anonymous (not a player)', () => {
    setup(false, true);
    const fakeTree = {} as UrlTree;
    routerSpy.createUrlTree.and.returnValue(fakeTree);

    const result = TestBed.runInInjectionContext(() => playerGuard({} as any, {} as any));

    expect(routerSpy.createUrlTree).toHaveBeenCalledWith(['/']);
    expect(result).toBe(fakeTree);
  });

  it('redirects to "/auth/login" when the user is not authenticated at all', () => {
    setup(false, false);
    const fakeTree = {} as UrlTree;
    routerSpy.createUrlTree.and.returnValue(fakeTree);

    const result = TestBed.runInInjectionContext(() => playerGuard({} as any, {} as any));

    expect(routerSpy.createUrlTree).toHaveBeenCalledWith(['/auth/login']);
    expect(result).toBe(fakeTree);
  });
});
