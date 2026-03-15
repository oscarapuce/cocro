import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '@infrastructure/auth/auth.service';

/** Protège les routes réservées aux utilisateurs PLAYER / ADMIN.
 *  Un invité anonyme est redirigé vers la homepage publique. */
export const playerGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isPlayer()) return true;
  if (auth.isAnonymous()) return router.createUrlTree(['/']);
  return router.createUrlTree(['/auth/login']);
};
