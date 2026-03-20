import { HttpErrorResponse } from '@angular/common/http';
import { getNetworkErrorMessage, NetworkError, toNetworkError } from './network-error';

describe('network-error', () => {
  it('maps known backend codes to a French message', () => {
    const error = new HttpErrorResponse({
      status: 400,
      error: {
        errors: [{ code: 'AUTH_INVALID_CREDENTIALS', message: 'Invalid credentials' }],
      },
    });

    const networkError = toNetworkError(error);

    expect(networkError).toBeInstanceOf(NetworkError);
    expect(networkError.code).toBe('AUTH_INVALID_CREDENTIALS');
    expect(networkError.message).toBe('Identifiants incorrects.');
  });

  it('falls back to backend message when code is unknown', () => {
    const error = new HttpErrorResponse({
      status: 400,
      error: {
        errors: [{ code: 'UNKNOWN_CODE', message: 'Backend message' }],
      },
    });

    expect(toNetworkError(error).message).toBe('Backend message');
  });

  it('uses a network-specific fallback for unreachable server', () => {
    const error = new HttpErrorResponse({ status: 0, error: new ProgressEvent('error') });

    const networkError = toNetworkError(error);

    expect(networkError.isNetworkFailure).toBe(true);
    expect(networkError.message).toBe('Impossible de contacter le serveur.');
  });

  it('returns a friendly message from a NetworkError instance', () => {
    const error = new HttpErrorResponse({
      status: 404,
      error: {
        errors: [{ code: 'SESSION_NOT_FOUND', message: 'Session not found' }],
      },
    });

    expect(getNetworkErrorMessage(toNetworkError(error), 'Fallback')).toBe('Session introuvable.');
  });
});
