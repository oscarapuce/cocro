/**
 * Application-layer helper to extract a human-readable message from any error.
 * Does NOT depend on HttpErrorResponse or Angular — stays infrastructure-free.
 * The NetworkError class (infrastructure) also extends Error, so `error.message`
 * covers both cases when it has already been transformed by the interceptor.
 */
export function getNetworkErrorMessage(error: unknown, fallback = 'Une erreur est survenue.'): string {
  if (error instanceof Error && error.message) {
    return error.message;
  }
  return fallback;
}
