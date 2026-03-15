export type SessionStatus = 'CREATING' | 'PLAYING' | 'SCORING' | 'ENDED' | 'INTERRUPTED';
export type CommandType = 'PLACE_LETTER' | 'CLEAR_CELL';

export interface CreateSessionRequest {
  gridId: string;
}

export interface SessionCreationResponse {
  sessionId: string;
  shareCode: string;
}

export interface JoinSessionRequest {
  shareCode: string;
}

export interface SessionJoinResponse {
  sessionId: string;
  participantCount: number;
}

export interface LeaveSessionRequest {
  shareCode: string;
}

export interface SessionLeaveResponse {
  sessionId: string;
}

export interface StartSessionRequest {
  shareCode: string;
}

export interface StartSessionResponse {
  sessionId: string;
  participantCount: number;
}

export interface CellStateDto {
  x: number;
  y: number;
  letter: string;
}

export interface SessionStateResponse {
  sessionId: string;
  shareCode: string;
  revision: number;
  cells: CellStateDto[];
}
