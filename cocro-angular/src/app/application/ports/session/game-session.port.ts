import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';
import {
  CreateSessionRequest,
  JoinSessionRequest,
  LeaveSessionRequest,
  SessionLeaveResponse,
  SessionFullResponse,
  SessionStateResponse,
} from '@domain/models/session.model';

export interface GameSessionPort {
  createSession(dto: CreateSessionRequest): Observable<SessionFullResponse>;
  joinSession(dto: JoinSessionRequest): Observable<SessionFullResponse>;
  leaveSession(dto: LeaveSessionRequest): Observable<SessionLeaveResponse>;
  getState(shareCode: string): Observable<SessionStateResponse>;
}

export const GAME_SESSION_PORT = new InjectionToken<GameSessionPort>('GAME_SESSION_PORT');
