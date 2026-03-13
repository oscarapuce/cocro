import { CommandType, SessionStatus } from './session.models';

export type SessionEventType =
  | 'SessionWelcome'
  | 'ParticipantJoined'
  | 'ParticipantLeft'
  | 'SessionStarted'
  | 'GridUpdated'
  | 'SyncRequired';

export interface SessionEvent {
  type: SessionEventType;
}

export interface SessionWelcomeEvent extends SessionEvent {
  type: 'SessionWelcome';
  shareCode: string;
  topicToSubscribe: string;
  participantCount: number;
  status: SessionStatus;
  gridRevision: number;
}

export interface ParticipantJoinedEvent extends SessionEvent {
  type: 'ParticipantJoined';
  userId: string;
  participantCount: number;
}

export interface ParticipantLeftEvent extends SessionEvent {
  type: 'ParticipantLeft';
  userId: string;
  participantCount: number;
  reason: 'explicit' | 'timeout';
}

export interface SessionStartedEvent extends SessionEvent {
  type: 'SessionStarted';
  participantCount: number;
}

export interface GridUpdatedEvent extends SessionEvent {
  type: 'GridUpdated';
  actorId: string;
  posX: number;
  posY: number;
  commandType: CommandType;
  letter?: string;
}

export interface SyncRequiredEvent extends SessionEvent {
  type: 'SyncRequired';
  currentRevision: number;
}
