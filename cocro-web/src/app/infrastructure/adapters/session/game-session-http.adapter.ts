import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { GameSessionPort } from '@application/ports/session/game-session.port';
import {
  CreateSessionRequest,
  GridCheckResponse,
  JoinSessionRequest,
  LeaveSessionRequest,
  SessionCreatedResponse,
  SessionLeaveResponse,
  SessionFullResponse,
  SessionStateResponse,
} from '@domain/models/session.model';
import { environment } from '@infrastructure/environment';

@Injectable({ providedIn: 'root' })
export class GameSessionHttpAdapter implements GameSessionPort {
  private readonly baseUrl = `${environment.apiBaseUrl}/api/sessions`;

  constructor(private http: HttpClient) {}

  createSession(dto: CreateSessionRequest): Observable<SessionCreatedResponse> {
    return this.http.post<SessionCreatedResponse>(this.baseUrl, dto);
  }

  joinSession(dto: JoinSessionRequest): Observable<SessionFullResponse> {
    return this.http.post<SessionFullResponse>(`${this.baseUrl}/join`, dto);
  }

  leaveSession(dto: LeaveSessionRequest): Observable<SessionLeaveResponse> {
    return this.http.post<SessionLeaveResponse>(`${this.baseUrl}/leave`, dto);
  }

  getState(shareCode: string): Observable<SessionStateResponse> {
    return this.http.get<SessionStateResponse>(`${this.baseUrl}/${shareCode}/state`);
  }

  syncSession(shareCode: string): Observable<SessionFullResponse> {
    return this.http.post<SessionFullResponse>(`${this.baseUrl}/${shareCode}/sync`, {});
  }

  checkGrid(shareCode: string): Observable<GridCheckResponse> {
    return this.http.post<GridCheckResponse>(`${this.baseUrl}/${shareCode}/check`, {});
  }
}
