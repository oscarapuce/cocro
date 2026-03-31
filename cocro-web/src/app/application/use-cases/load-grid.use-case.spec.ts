import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { LoadGridUseCase } from './load-grid.use-case';
import { GridPort, GRID_PORT } from '@application/ports/grid/grid.port';
import { Grid } from '@domain/models/grid.model';

describe('LoadGridUseCase', () => {
  let useCase: LoadGridUseCase;
  let mockGridPort: jest.Mocked<GridPort>;

  const GRID_STUB: Grid = {
    id: 'grid-1',
    title: 'Test Grid',
    width: 5,
    height: 5,
    cells: [],
  };

  beforeEach(() => {
    mockGridPort = {
      getGrid: jest.fn(),
      getMyGrids: jest.fn(),
      submitGrid: jest.fn(),
      patchGrid: jest.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        LoadGridUseCase,
        { provide: GRID_PORT, useValue: mockGridPort },
      ],
    });

    useCase = TestBed.inject(LoadGridUseCase);
  });

  it('should be created', () => {
    expect(useCase).toBeTruthy();
  });

  it('should delegate to gridPort.getGrid and return the grid', (done) => {
    mockGridPort.getGrid.mockReturnValue(of(GRID_STUB));

    useCase.execute('grid-1').subscribe((grid) => {
      expect(grid).toEqual(GRID_STUB);
      expect(mockGridPort.getGrid).toHaveBeenCalledWith('grid-1');
      done();
    });
  });

  it('should propagate errors from gridPort.getGrid', (done) => {
    mockGridPort.getGrid.mockReturnValue(throwError(() => new Error('Not found')));

    useCase.execute('bad-id').subscribe({
      error: (err) => {
        expect(err.message).toBe('Not found');
        done();
      },
    });
  });
});
