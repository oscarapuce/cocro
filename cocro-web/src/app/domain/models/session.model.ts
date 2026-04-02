import { GridTemplateResponse } from './grid-template.model';

export type SessionStatus = 'PLAYING' | 'ENDED' | 'INTERRUPTED';
export type CommandType = 'PLACE_LETTER' | 'CLEAR_CELL';

export interface SessionCreatedResponse {
  sessionId: string;
  shareCode: string;
}

export interface GridCheckResponse {
  shareCode: string;
  isComplete: boolean;
  isCorrect: boolean;
  correctCount: number;
  totalCount: number;
  filledCount: number;
  wrongCount: number;
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

export interface ParticipantInfo {
  userId: string;
  username: string;
  status: 'JOINED' | 'LEFT';
  isCreator: boolean;
}

export interface SessionFullResponse {
  sessionId: string;
  shareCode: string;
  status: SessionStatus;
  participantCount: number;
  participants: ParticipantInfo[];
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
