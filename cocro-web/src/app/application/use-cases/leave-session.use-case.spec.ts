import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { LeaveSessionUseCase } from './leave-session.use-case';
import { GameSessionPort, GAME_SESSION_PORT } from '@application/ports/session/game-session.port';
import { SessionLeaveResponse } from '@domain/models/session.model';

const LEAVE_STUB: SessionLeaveResponse = {
  sessionId: 'sess-10',
};

describe('LeaveSessionUseCase', () => {
  let useCase: LeaveSessionUseCase;
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
        LeaveSessionUseCase,
        { provide: GAME_SESSION_PORT, useValue: mockSessionPort },
      ],
    });

    useCase = TestBed.inject(LeaveSessionUseCase);
  });

  it('should be created', () => {
    expect(useCase).toBeTruthy();
  });

  it('should delegate to sessionPort.leaveSession with the correct shareCode', (done) => {
    mockSessionPort.leaveSession.mockReturnValue(of(LEAVE_STUB));

    useCase.execute('CODE-XY').subscribe((result) => {
      expect(mockSessionPort.leaveSession).toHaveBeenCalledWith({ shareCode: 'CODE-XY' });
      expect(result).toEqual(LEAVE_STUB);
      done();
    });
  });

  it('should return the SessionLeaveResponse from the port', (done) => {
    mockSessionPort.leaveSession.mockReturnValue(of(LEAVE_STUB));

    useCase.execute('CODE-XY').subscribe((result) => {
      expect(result).toEqual(LEAVE_STUB);
      done();
    });
  });

  it('should propagate error from leaveSession', (done) => {
    const error = new Error('leave failed');
    mockSessionPort.leaveSession.mockReturnValue(throwError(() => error));

    useCase.execute('CODE-XY').subscribe({
      next: () => done.fail('expected error'),
      error: (err) => {
        expect(err).toBe(error);
        done();
      },
    });
  });
});
