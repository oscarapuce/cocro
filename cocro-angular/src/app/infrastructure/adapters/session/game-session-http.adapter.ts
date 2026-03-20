import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { GameSessionPort } from '@application/ports/session/game-session.port';
import {
  CreateSessionRequest, SessionCreationResponse,
  JoinSessionRequest, SessionJoinResponse,
  LeaveSessionRequest, SessionLeaveResponse,
  StartSessionRequest, StartSessionResponse,
  SessionStateResponse
} from '@domain/models/session.model';
import { environment } from '@infrastructure/environment';

@Injectable({ providedIn: 'root' })
export class GameSessionHttpAdapter implements GameSessionPort {
  private readonly baseUrl = `${environment.apiBaseUrl}/api/sessions`;

  constructor(private http: HttpClient) {}

  createSession(request: CreateSessionRequest): Observable<SessionCreationResponse> {
    return this.http.post<SessionCreationResponse>(this.baseUrl, request);
  }

  joinSession(request: JoinSessionRequest): Observable<SessionJoinResponse> {
    return this.http.post<SessionJoinResponse>(`${this.baseUrl}/join`, request);
  }

  leaveSession(request: LeaveSessionRequest): Observable<SessionLeaveResponse> {
    return this.http.post<SessionLeaveResponse>(`${this.baseUrl}/leave`, request);
  }

  startSession(request: StartSessionRequest): Observable<StartSessionResponse> {
    return this.http.post<StartSessionResponse>(`${this.baseUrl}/start`, request);
  }

  getState(shareCode: string): Observable<SessionStateResponse> {
    return this.http.get<SessionStateResponse>(`${this.baseUrl}/${shareCode}/state`);
  }
}
