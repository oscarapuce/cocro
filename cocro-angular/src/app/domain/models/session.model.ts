import { GridTemplateResponse } from './grid-template.model';

export type SessionStatus = 'PLAYING' | 'ENDED' | 'INTERRUPTED';
export type CommandType = 'PLACE_LETTER' | 'CLEAR_CELL';

export interface SessionCreatedResponse {
  sessionId: string;
  shareCode: string;
}

export interface GridCheckResponse {
  isComplete: boolean;
  isCorrect: boolean;
  correctCount: number;
  totalCount: number;
}

export interface CreateSessionRequest {
  gridId: string;
}

export interface JoinSessionRequest {
  shareCode: string;
}

export interface LeaveSessionRequest {
  shareCode: string;
}

export interface SessionLeaveResponse {
  sessionId: string;
}

export interface CellStateDto {
  x: number;
  y: number;
  letter: string;
}

export interface SessionFullResponse {
  sessionId: string;
  shareCode: string;
  status: SessionStatus;
  participantCount: number;
  topicToSubscribe: string;
  gridTemplate: GridTemplateResponse;
  gridRevision: number;
  cells: CellStateDto[];
}

export interface SessionStateResponse {
  sessionId: string;
  shareCode: string;
  revision: number;
  cells: CellStateDto[];
}
