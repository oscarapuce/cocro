import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { playerGuard } from './player.guard';
import { AuthService } from '@infrastructure/auth/auth.service';
import { signal } from '@angular/core';

describe('playerGuard', () => {
  let mockRouter: { createUrlTree: jest.Mock };
  let mockAuth: {
    isPlayer: ReturnType<typeof signal<boolean>>;
    isAnonymous: ReturnType<typeof signal<boolean>>;
  };

  beforeEach(() => {
    mockRouter = { createUrlTree: jest.fn().mockReturnValue('url-tree' as unknown as UrlTree) };
    mockAuth = {
      isPlayer: signal(false),
      isAnonymous: signal(false),
    };

    TestBed.configureTestingModule({
      providers: [
        { provide: Router, useValue: mockRouter },
        { provide: AuthService, useValue: mockAuth },
      ],
    });
  });

  it('should return true when user is a player', () => {
    mockAuth.isPlayer.set(true);
    const result = TestBed.runInInjectionContext(() =>
      playerGuard({} as any, {} as any),
    );
    expect(result).toBe(true);
  });

  it('should redirect to / when user is anonymous', () => {
    mockAuth.isPlayer.set(false);
    mockAuth.isAnonymous.set(true);
    TestBed.runInInjectionContext(() =>
      playerGuard({} as any, {} as any),
    );
    expect(mockRouter.createUrlTree).toHaveBeenCalledWith(['/']);
  });

  it('should redirect to /auth/login when user is neither player nor anonymous', () => {
    mockAuth.isPlayer.set(false);
    mockAuth.isAnonymous.set(false);
    TestBed.runInInjectionContext(() =>
      playerGuard({} as any, {} as any),
    );
    expect(mockRouter.createUrlTree).toHaveBeenCalledWith(['/auth/login']);
  });
});
