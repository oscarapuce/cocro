import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';
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

export interface GameSessionPort {
  createSession(dto: CreateSessionRequest): Observable<SessionCreatedResponse>;
  joinSession(dto: JoinSessionRequest): Observable<SessionFullResponse>;
  leaveSession(dto: LeaveSessionRequest): Observable<SessionLeaveResponse>;
  getState(shareCode: string): Observable<SessionStateResponse>;
  syncSession(shareCode: string): Observable<SessionFullResponse>;
  checkGrid(shareCode: string): Observable<GridCheckResponse>;
}

export const GAME_SESSION_PORT = new InjectionToken<GameSessionPort>('GAME_SESSION_PORT');
