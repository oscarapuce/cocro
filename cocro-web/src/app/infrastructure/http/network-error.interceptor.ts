import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { Router } from '@angular/router';
import { ToastService } from '@presentation/shared/components/toast/toast.service';
import { AuthService } from '@infrastructure/auth/auth.service';
import { NetworkError, toNetworkError } from './network-error';

export const networkErrorInterceptor: HttpInterceptorFn = (req, next) => {
  const toast = inject(ToastService);
  const auth = inject(AuthService);
  const router = inject(Router);

  return next(req).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse)) {
        return throwError(() => error);
      }

      const networkError = toNetworkError(error);

      if (networkError.status === 401 && !req.url.includes('/auth/')) {
        auth.logout();
        router.navigate(['/auth/login']);
      } else if (networkError.isNetworkFailure || networkError.isServerFailure) {
        toast.error(networkError.message);
      }

      return throwError(() => networkError);
    }),
  );
};
