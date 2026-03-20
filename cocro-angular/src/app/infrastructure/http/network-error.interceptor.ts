import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { ToastService } from '@presentation/shared/components/toast/toast.service';
import { NetworkError, toNetworkError } from './network-error';

export const networkErrorInterceptor: HttpInterceptorFn = (req, next) => {
  const toast = inject(ToastService);

  return next(req).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse)) {
        return throwError(() => error);
      }

      const networkError = toNetworkError(error);

      if (networkError.isNetworkFailure || networkError.isServerFailure) {
        toast.error(networkError.message);
      }

      return throwError(() => networkError);
    }),
  );
};
