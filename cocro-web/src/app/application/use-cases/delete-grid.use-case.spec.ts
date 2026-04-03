import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { DeleteGridUseCase } from './delete-grid.use-case';
import { GridPort, GRID_PORT } from '@application/ports/grid/grid.port';

describe('DeleteGridUseCase', () => {
  let useCase: DeleteGridUseCase;
  let mockGridPort: jest.Mocked<GridPort>;

  beforeEach(() => {
    mockGridPort = {
      getGrid: jest.fn(),
      getMyGrids: jest.fn(),
      submitGrid: jest.fn(),
      patchGrid: jest.fn(),
      deleteGrid: jest.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        DeleteGridUseCase,
        { provide: GRID_PORT, useValue: mockGridPort },
      ],
    });

    useCase = TestBed.inject(DeleteGridUseCase);
  });

  it('should be created', () => {
    expect(useCase).toBeTruthy();
  });

  it('should delegate to gridPort.deleteGrid with the correct gridId', (done) => {
    mockGridPort.deleteGrid.mockReturnValue(of(undefined));

    useCase.execute('grid-abc').subscribe(() => {
      expect(mockGridPort.deleteGrid).toHaveBeenCalledWith('grid-abc');
      expect(mockGridPort.deleteGrid).toHaveBeenCalledTimes(1);
      done();
    });
  });

  it('should complete without emitting a value on success', (done) => {
    mockGridPort.deleteGrid.mockReturnValue(of(undefined));
    const nextSpy = jest.fn();

    useCase.execute('grid-abc').subscribe({
      next: nextSpy,
      complete: () => {
        expect(nextSpy).toHaveBeenCalledWith(undefined);
        done();
      },
    });
  });

  it('should propagate error from deleteGrid', (done) => {
    const error = new Error('delete failed');
    mockGridPort.deleteGrid.mockReturnValue(throwError(() => error));

    useCase.execute('grid-abc').subscribe({
      next: () => done.fail('expected error'),
      error: (err) => {
        expect(err).toBe(error);
        done();
      },
    });
  });
});
