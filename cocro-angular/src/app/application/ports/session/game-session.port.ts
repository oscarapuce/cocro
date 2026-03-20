import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';
import {
  CreateSessionRequest, SessionCreationResponse,
  JoinSessionRequest, SessionJoinResponse,
  LeaveSessionRequest, SessionLeaveResponse,
  StartSessionRequest, StartSessionResponse,
  SessionStateResponse
} from '@domain/models/session.model';

export interface GameSessionPort {
  createSession(request: CreateSessionRequest): Observable<SessionCreationResponse>;
  joinSession(request: JoinSessionRequest): Observable<SessionJoinResponse>;
  leaveSession(request: LeaveSessionRequest): Observable<SessionLeaveResponse>;
  startSession(request: StartSessionRequest): Observable<StartSessionResponse>;
  getState(shareCode: string): Observable<SessionStateResponse>;
}

export const GAME_SESSION_PORT = new InjectionToken<GameSessionPort>('GAME_SESSION_PORT');
