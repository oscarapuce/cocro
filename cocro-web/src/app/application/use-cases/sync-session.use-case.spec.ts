import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { SyncSessionUseCase } from './sync-session.use-case';
import { GameSessionPort, GAME_SESSION_PORT } from '@application/ports/session/game-session.port';
import { SessionFullResponse } from '@domain/models/session.model';

const SYNC_STUB: SessionFullResponse = {
  sessionId: 'sess-77',
  shareCode: 'SYNC01',
  status: 'PLAYING',
  participantCount: 2,
  participants: [],
  topicToSubscribe: '/topic/session/SYNC01',
  gridTemplate: {} as any,
  gridRevision: 5,
  cells: [{ x: 0, y: 0, letter: 'A' }],
};

describe('SyncSessionUseCase', () => {
  let useCase: SyncSessionUseCase;
  let mockSessionPort: jest.Mocked<GameSessionPort>;

  beforeEach(() => {
    mockSessionPort = {
      createSession: jest.fn(),
      joinSession: jest.fn(),
      leaveSession: jest.fn(),
      getState: jest.fn(),
      syncSession: jest.fn(),
      checkGrid: jest.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        SyncSessionUseCase,
        { provide: GAME_SESSION_PORT, useValue: mockSessionPort },
      ],
    });

    useCase = TestBed.inject(SyncSessionUseCase);
  });

  it('should be created', () => {
    expect(useCase).toBeTruthy();
  });

  it('should delegate to sessionPort.syncSession with the correct shareCode', (done) => {
    mockSessionPort.syncSession.mockReturnValue(of(SYNC_STUB));

    useCase.execute('SYNC01').subscribe(() => {
      expect(mockSessionPort.syncSession).toHaveBeenCalledWith('SYNC01');
      done();
    });
  });

  it('should return the SessionFullResponse from the port', (done) => {
    mockSessionPort.syncSession.mockReturnValue(of(SYNC_STUB));

    useCase.execute('SYNC01').subscribe((result) => {
      expect(result).toEqual(SYNC_STUB);
      done();
    });
  });

  it('should propagate error from syncSession', (done) => {
    const error = new Error('sync failed');
    mockSessionPort.syncSession.mockReturnValue(throwError(() => error));

    useCase.execute('SYNC01').subscribe({
      next: () => done.fail('expected error'),
      error: (err) => {
        expect(err).toBe(error);
        done();
      },
    });
  });
});
