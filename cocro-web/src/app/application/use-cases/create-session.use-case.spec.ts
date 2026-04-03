import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { CreateSessionUseCase } from './create-session.use-case';
import { GameSessionPort, GAME_SESSION_PORT } from '@application/ports/session/game-session.port';
import { SessionCreatedResponse, SessionFullResponse } from '@domain/models/session.model';

const CREATED_STUB: SessionCreatedResponse = {
  sessionId: 'sess-42',
  shareCode: 'XYZ789',
};

const FULL_STUB: SessionFullResponse = {
  sessionId: 'sess-42',
  shareCode: 'XYZ789',
  status: 'PLAYING',
  participantCount: 1,
  participants: [],
  topicToSubscribe: '/topic/session/XYZ789',
  gridTemplate: {} as any,
  gridRevision: 0,
  cells: [],
};

describe('CreateSessionUseCase', () => {
  let useCase: CreateSessionUseCase;
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
        CreateSessionUseCase,
        { provide: GAME_SESSION_PORT, useValue: mockSessionPort },
      ],
    });

    useCase = TestBed.inject(CreateSessionUseCase);
  });

  it('should be created', () => {
    expect(useCase).toBeTruthy();
  });

  describe('happy path', () => {
    beforeEach(() => {
      mockSessionPort.createSession.mockReturnValue(of(CREATED_STUB));
      mockSessionPort.joinSession.mockReturnValue(of(FULL_STUB));
    });

    it('should call createSession with the provided gridId', (done) => {
      useCase.execute('grid-99').subscribe(() => {
        expect(mockSessionPort.createSession).toHaveBeenCalledWith({ gridId: 'grid-99' });
        done();
      });
    });

    it('should call joinSession with the shareCode returned by createSession', (done) => {
      useCase.execute('grid-99').subscribe(() => {
        expect(mockSessionPort.joinSession).toHaveBeenCalledWith({ shareCode: 'XYZ789' });
        done();
      });
    });

    it('should return the SessionFullResponse from joinSession', (done) => {
      useCase.execute('grid-99').subscribe((result) => {
        expect(result).toEqual(FULL_STUB);
        done();
      });
    });

    it('should call createSession before joinSession', (done) => {
      const callOrder: string[] = [];
      mockSessionPort.createSession.mockImplementation(() => {
        callOrder.push('createSession');
        return of(CREATED_STUB);
      });
      mockSessionPort.joinSession.mockImplementation(() => {
        callOrder.push('joinSession');
        return of(FULL_STUB);
      });

      useCase.execute('grid-99').subscribe(() => {
        expect(callOrder).toEqual(['createSession', 'joinSession']);
        done();
      });
    });
  });

  describe('error propagation', () => {
    it('should propagate error from createSession and not call joinSession', (done) => {
      const error = new Error('create failed');
      mockSessionPort.createSession.mockReturnValue(throwError(() => error));

      useCase.execute('grid-99').subscribe({
        next: () => done.fail('expected error'),
        error: (err) => {
          expect(err).toBe(error);
          expect(mockSessionPort.joinSession).not.toHaveBeenCalled();
          done();
        },
      });
    });

    it('should propagate error from joinSession after session was created', (done) => {
      const error = new Error('join failed');
      mockSessionPort.createSession.mockReturnValue(of(CREATED_STUB));
      mockSessionPort.joinSession.mockReturnValue(throwError(() => error));

      useCase.execute('grid-99').subscribe({
        next: () => done.fail('expected error'),
        error: (err) => {
          expect(err).toBe(error);
          expect(mockSessionPort.createSession).toHaveBeenCalledTimes(1);
          done();
        },
      });
    });
  });
});
