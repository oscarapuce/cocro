import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  CreateSessionRequest,
  JoinSessionRequest,
  LeaveSessionRequest,
  SessionCreationResponse,
  SessionJoinResponse,
  SessionLeaveResponse,
  SessionStateResponse,
  StartSessionRequest,
  StartSessionResponse,
} from '@domain/models/session.model';

@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly base = `${environment.apiBaseUrl}/api/sessions`;

  constructor(private http: HttpClient) {}

  createSession(dto: CreateSessionRequest): Observable<SessionCreationResponse> {
    return this.http.post<SessionCreationResponse>(this.base, dto);
  }

  joinSession(dto: JoinSessionRequest): Observable<SessionJoinResponse> {
    return this.http.post<SessionJoinResponse>(`${this.base}/join`, dto);
  }

  leaveSession(dto: LeaveSessionRequest): Observable<SessionLeaveResponse> {
    return this.http.post<SessionLeaveResponse>(`${this.base}/leave`, dto);
  }

  startSession(dto: StartSessionRequest): Observable<StartSessionResponse> {
    return this.http.post<StartSessionResponse>(`${this.base}/start`, dto);
  }

  getState(shareCode: string): Observable<SessionStateResponse> {
    return this.http.get<SessionStateResponse>(`${this.base}/${shareCode}/state`);
  }
}
