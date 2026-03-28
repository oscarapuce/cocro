import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { CheckGridUseCase } from './check-grid.use-case';
import { GameSessionPort, GAME_SESSION_PORT } from '@application/ports/session/game-session.port';
import { GridCheckResponse } from '@domain/models/session.model';

describe('CheckGridUseCase', () => {
  let useCase: CheckGridUseCase;
  let mockSessionPort: jest.Mocked<GameSessionPort>;

  const CHECK_STUB: GridCheckResponse = {
    isComplete: false,
    correctCount: 3,
    totalCount: 10,
  };

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
        CheckGridUseCase,
        { provide: GAME_SESSION_PORT, useValue: mockSessionPort },
      ],
    });

    useCase = TestBed.inject(CheckGridUseCase);
  });

  it('should be created', () => {
    expect(useCase).toBeTruthy();
  });

  it('should delegate to sessionPort.checkGrid and return the result', (done) => {
    mockSessionPort.checkGrid.mockReturnValue(of(CHECK_STUB));

    useCase.execute('ABC123').subscribe((result) => {
      expect(result).toEqual(CHECK_STUB);
      expect(mockSessionPort.checkGrid).toHaveBeenCalledWith('ABC123');
      done();
    });
  });
});
