import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { JoinSessionUseCase } from './join-session.use-case';
import { AuthPort, AUTH_PORT } from '@application/ports/auth/auth.port';
import { GameSessionPort, GAME_SESSION_PORT } from '@application/ports/session/game-session.port';
import { SessionFullResponse } from '@domain/models/session.model';
import { AuthResponse } from '@domain/models/auth.model';

const SESSION_STUB: SessionFullResponse = {
  sessionId: 'sess-1',
  shareCode: 'ABC123',
  status: 'PLAYING',
  participantCount: 1,
  participants: [],
  topicToSubscribe: '/topic/session/ABC123',
  gridTemplate: {} as any,
  gridRevision: 0,
  cells: [],
};

const GUEST_STUB: AuthResponse = {
  token: 'guest-token',
  userId: 'guest-1',
  username: 'guest-guest-1',
  role: 'PLAYER',
};

describe('JoinSessionUseCase', () => {
  let useCase: JoinSessionUseCase;
  let mockAuthPort: jest.Mocked<AuthPort>;
  let mockSessionPort: jest.Mocked<GameSessionPort>;

  beforeEach(() => {
    mockAuthPort = {
      login: jest.fn(),
      register: jest.fn(),
      createGuest: jest.fn(),
      isAuthenticated: jest.fn(),
    };

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
        JoinSessionUseCase,
        { provide: AUTH_PORT, useValue: mockAuthPort },
        { provide: GAME_SESSION_PORT, useValue: mockSessionPort },
      ],
    });

    useCase = TestBed.inject(JoinSessionUseCase);
  });

  it('should be created', () => {
    expect(useCase).toBeTruthy();
  });

  describe('when authenticated', () => {
    beforeEach(() => {
      mockAuthPort.isAuthenticated.mockReturnValue(true);
      mockSessionPort.joinSession.mockReturnValue(of(SESSION_STUB));
    });

    it('should call joinSession with the shareCode', (done) => {
      useCase.execute('ABC123').subscribe((result) => {
        expect(mockSessionPort.joinSession).toHaveBeenCalledWith({ shareCode: 'ABC123' });
        expect(result).toEqual(SESSION_STUB);
        done();
      });
    });

    it('should NOT call createGuest when already authenticated', (done) => {
      useCase.execute('ABC123').subscribe(() => {
        expect(mockAuthPort.createGuest).not.toHaveBeenCalled();
        done();
      });
    });
  });

  describe('when NOT authenticated (anonymous)', () => {
    beforeEach(() => {
      mockAuthPort.isAuthenticated.mockReturnValue(false);
      mockAuthPort.createGuest.mockReturnValue(of(GUEST_STUB));
      mockSessionPort.joinSession.mockReturnValue(of(SESSION_STUB));
    });

    it('should auto-create a guest session before joining', (done) => {
      useCase.execute('ABC123').subscribe((result) => {
        expect(mockAuthPort.createGuest).toHaveBeenCalledTimes(1);
        expect(mockSessionPort.joinSession).toHaveBeenCalledWith({ shareCode: 'ABC123' });
        expect(result).toEqual(SESSION_STUB);
        done();
      });
    });

    it('should call createGuest before joinSession (ordering)', (done) => {
      const callOrder: string[] = [];
      mockAuthPort.createGuest.mockImplementation(() => {
        callOrder.push('createGuest');
        return of(GUEST_STUB);
      });
      mockSessionPort.joinSession.mockImplementation(() => {
        callOrder.push('joinSession');
        return of(SESSION_STUB);
      });

      useCase.execute('ABC123').subscribe(() => {
        expect(callOrder).toEqual(['createGuest', 'joinSession']);
        done();
      });
    });
  });

  describe('error propagation', () => {
    it('should propagate error from joinSession when authenticated', (done) => {
      const error = new Error('join failed');
      mockAuthPort.isAuthenticated.mockReturnValue(true);
      mockSessionPort.joinSession.mockReturnValue(throwError(() => error));

      useCase.execute('ABC123').subscribe({
        next: () => done.fail('expected error'),
        error: (err) => {
          expect(err).toBe(error);
          done();
        },
      });
    });

    it('should propagate error from joinSession after guest creation', (done) => {
      const error = new Error('join failed after guest');
      mockAuthPort.isAuthenticated.mockReturnValue(false);
      mockAuthPort.createGuest.mockReturnValue(of(GUEST_STUB));
      mockSessionPort.joinSession.mockReturnValue(throwError(() => error));

      useCase.execute('ABC123').subscribe({
        next: () => done.fail('expected error'),
        error: (err) => {
          expect(err).toBe(error);
          done();
        },
      });
    });

    it('should propagate error from createGuest', (done) => {
      const error = new Error('guest creation failed');
      mockAuthPort.isAuthenticated.mockReturnValue(false);
      mockAuthPort.createGuest.mockReturnValue(throwError(() => error));

      useCase.execute('ABC123').subscribe({
        next: () => done.fail('expected error'),
        error: (err) => {
          expect(err).toBe(error);
          done();
        },
      });
    });
  });
});
