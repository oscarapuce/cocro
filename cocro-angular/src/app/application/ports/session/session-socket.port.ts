import { InjectionToken } from '@angular/core';
import { SessionEvent } from '@domain/models/session-events.model';

export interface SessionSocketPort {
  connect(token: string, shareCode: string, onEvent: (event: SessionEvent) => void): void;
  sendGridUpdate(shareCode: string, payload: { posX: number; posY: number; commandType: string; letter?: string }): void;
  disconnect(): void;
}

export const SESSION_SOCKET_PORT = new InjectionToken<SessionSocketPort>('SESSION_SOCKET_PORT');
