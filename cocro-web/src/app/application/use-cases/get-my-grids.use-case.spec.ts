import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { GetMyGridsUseCase } from './get-my-grids.use-case';
import { GridPort, GRID_PORT } from '@application/ports/grid/grid.port';
import { GridSummary } from '@domain/models/grid-summary.model';

describe('GetMyGridsUseCase', () => {
  let useCase: GetMyGridsUseCase;
  let mockGridPort: jest.Mocked<GridPort>;

  beforeEach(() => {
    mockGridPort = {
      getGrid: jest.fn(),
      getMyGrids: jest.fn(),
      submitGrid: jest.fn(),
      patchGrid: jest.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        GetMyGridsUseCase,
        { provide: GRID_PORT, useValue: mockGridPort },
      ],
    });

    useCase = TestBed.inject(GetMyGridsUseCase);
  });

  it('should be created', () => {
    expect(useCase).toBeTruthy();
  });

  it('should delegate to gridPort.getMyGrids', (done) => {
    const grids: GridSummary[] = [
      {
        gridId: 'grid-1',
        title: 'Ma grille',
        width: 10,
        height: 10,
        difficulty: 'EASY',
        createdAt: '2026-03-20T10:00:00Z',
        updatedAt: '2026-03-20T12:00:00Z',
      },
    ];
    mockGridPort.getMyGrids.mockReturnValue(of(grids));

    useCase.execute().subscribe((result) => {
      expect(result).toEqual(grids);
      expect(mockGridPort.getMyGrids).toHaveBeenCalledTimes(1);
      done();
    });
  });
});
