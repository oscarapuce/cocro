import { HttpErrorResponse } from '@angular/common/http';

export interface ApiErrorPayload {
  code: string;
  message: string;
  context?: Record<string, string>;
}

export interface ApiErrorResponse {
  errors: ApiErrorPayload[];
}

export class NetworkError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code: string | null,
    readonly errors: ApiErrorPayload[],
    readonly original: HttpErrorResponse,
  ) {
    super(message);
    this.name = 'NetworkError';
  }

  get isNetworkFailure(): boolean {
    return this.status === 0;
  }

  get isServerFailure(): boolean {
    return this.status >= 500;
  }
}

const ERROR_MESSAGES: Record<string, string> = {
  AUTH_INVALID_CREDENTIALS: 'Identifiants incorrects.',
  AUTH_USERNAME_ALREADY_EXISTS: 'Ce pseudo est déjà pris.',
  AUTH_USERNAME_INVALID: 'Pseudo invalide.',
  AUTH_EMAIL_INVALID: 'Email invalide.',
  AUTH_PASSWORD_INVALID: 'Mot de passe invalide.',
  AUTH_PASSWORD_TOO_WEAK: 'Mot de passe trop faible.',
  GRID_TITLE_MISSING: 'Le titre de la grille est requis.',
  GRID_INVALID_TITLE: 'Titre de grille invalide.',
  GRID_INVALID_GRID_ID: 'Identifiant de grille invalide.',
  GRID_DUPLICATE_LETTER_HASH: 'Une grille similaire existe déjà.',
  GRID_NOT_FOUND: 'Grille introuvable.',
  GRID_UNAUTHORIZED_CREATION: 'Vous devez être connecté pour créer une grille.',
  GRID_UNAUTHORIZED_MODIFICATION: 'Vous ne pouvez pas modifier cette grille.',
  GRID_INVALID_CELL_COUNT: 'La taille de la grille est invalide.',
  GRID_INVALID_LETTER: 'Une lettre de la grille est invalide.',
  GRID_INVALID_CLUE_COUNT: 'Le nombre d’indices est invalide.',
  GRID_DUPLICATE_CLUE_DIRECTION: 'Deux indices ont la même direction dans une cellule.',
  GRID_INVALID_SAFE_STRING: 'Le contenu texte contient des caractères non autorisés.',
  SESSION_FULL: 'La session est pleine ou déjà rejointe.',
  SESSION_NOT_FOUND: 'Session introuvable.',
  SESSION_INVALID_SHARE_CODE: 'Code de partage invalide.',
  SESSION_CANNOT_CREATE_WHEN_UNAUTHORIZED: 'Vous devez être connecté pour rejoindre ou créer une session.',
  SESSION_NOT_CREATOR: 'Seul le créateur peut effectuer cette action.',
  SESSION_INVALID_STATUS_FOR_ACTION: 'Cette action n’est pas autorisée dans l’état actuel de la session.',
  SESSION_ALREADY_PARTICIPANT: 'Vous participez déjà à cette session.',
  SESSION_USER_NOT_IN_SESSION: 'Vous ne faites pas partie de cette session.',
  SESSION_NOT_INVITED: 'Vous n’êtes pas invité à cette session.',
  SESSION_GRID_NOT_SELECTED: 'Aucune grille n’est sélectionnée pour cette session.',
  SESSION_REFERENCE_GRID_NOT_FOUND: 'La grille liée à cette session est introuvable.',
  SESSION_NOT_ENOUGH_PARTICIPANTS: 'Il faut au moins un participant pour démarrer.',
  SESSION_INVALID_COMMAND: 'Commande de session invalide.',
  SESSION_LIMIT_REACHED: 'Limite de sessions atteinte (max 5).',
  SESSION_CANNOT_DELETE_ACTIVE: 'Impossible de supprimer une session avec des joueurs actifs.',
  GRID_HAS_ACTIVE_SESSIONS: 'Cette grille a des sessions actives et ne peut pas être supprimée.',
  GRID_STATE_INVALID_LETTER: 'Lettre invalide dans l\'état de jeu.',
  GRID_STATE_INVALID_POSITION: 'Position invalide dans la grille.',
  GRID_STATE_NOT_INITIALIZED: 'L’état de grille n’est pas initialisé.',
  UNAUTHORIZED: 'Vous devez être authentifié.',
  FORBIDDEN: 'Action interdite.',
  NOT_FOUND: 'Ressource introuvable.',
  BAD_REQUEST: 'Requête invalide.',
};

function extractApiErrors(payload: unknown): ApiErrorPayload[] {
  if (
    payload &&
    typeof payload === 'object' &&
    'errors' in payload &&
    Array.isArray((payload as ApiErrorResponse).errors)
  ) {
    return (payload as ApiErrorResponse).errors.filter(
      (error): error is ApiErrorPayload =>
        !!error &&
        typeof error.code === 'string' &&
        typeof error.message === 'string',
    );
  }

  return [];
}

function fallbackMessage(status: number): string {
  if (status === 0) {
    return 'Impossible de contacter le serveur.';
  }
  if (status >= 500) {
    return 'Le serveur a rencontré une erreur.';
  }
  return 'Une erreur réseau est survenue.';
}

export function toNetworkError(error: HttpErrorResponse): NetworkError {
  const errors = extractApiErrors(error.error);
  const first = errors[0];
  const code = first?.code ?? null;
  const mappedMessage = code ? ERROR_MESSAGES[code] : null;
  const message = mappedMessage ?? first?.message ?? fallbackMessage(error.status);

  return new NetworkError(message, error.status, code, errors, error);
}

