import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from '@infrastructure/auth/auth.service';

export const playerGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isPlayer()) {
    return true;
  }
  if (auth.isAnonymous()) {
    return router.createUrlTree(['/']);
  }
  return router.createUrlTree(['/auth/login']);
};
