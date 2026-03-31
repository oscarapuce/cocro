export interface SessionSummary {
  sessionId: string;
  shareCode: string;
  status: 'PLAYING' | 'INTERRUPTED' | 'ENDED';
  gridTitle: string;
  gridDimension: { width: number; height: number };
  authorName: string;
  participantCount: number;
  role: 'CREATOR' | 'PARTICIPANT';
  createdAt: string;
  updatedAt: string;
}

