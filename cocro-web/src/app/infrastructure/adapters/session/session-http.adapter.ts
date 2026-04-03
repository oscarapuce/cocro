import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { SessionSummary } from '@domain/models/session-summary.model';
import { environment } from '@infrastructure/environment';
import { SessionManagementPort } from '@application/ports/session/session-management.port';

@Injectable({ providedIn: 'root' })
export class SessionHttpAdapter implements SessionManagementPort {
  private readonly baseUrl = `${environment.apiBaseUrl}/api/sessions`;

  constructor(private http: HttpClient) {}

  getMySessions(): Observable<SessionSummary[]> {
    return this.http.get<SessionSummary[]>(`${this.baseUrl}/mine`);
  }

  deleteSession(shareCode: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${shareCode}`);
  }
}

