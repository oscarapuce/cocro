import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';
import { SessionSummary } from '@domain/models/session-summary.model';

export interface SessionManagementPort {
  getMySessions(): Observable<SessionSummary[]>;
  deleteSession(shareCode: string): Observable<void>;
}

export const SESSION_MANAGEMENT_PORT = new InjectionToken<SessionManagementPort>('SESSION_MANAGEMENT_PORT');
