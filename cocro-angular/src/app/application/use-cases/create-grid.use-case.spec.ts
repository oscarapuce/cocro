import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { CreateGridUseCase } from './create-grid.use-case';
import { GridPort, GRID_PORT } from '@application/ports/grid/grid.port';
import { SubmitGridRequest, GridSubmitResponse } from '@application/dto/grid.dto';

describe('CreateGridUseCase', () => {
  let useCase: CreateGridUseCase;
  let mockGridPort: jest.Mocked<GridPort>;

  beforeEach(() => {
    mockGridPort = {
      getGrid: jest.fn(),
      submitGrid: jest.fn(),
      patchGrid: jest.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        CreateGridUseCase,
        { provide: GRID_PORT, useValue: mockGridPort },
      ],
    });

    useCase = TestBed.inject(CreateGridUseCase);
  });

  it('should be created', () => {
    expect(useCase).toBeTruthy();
  });

  it('should call gridPort.submitGrid and return gridId', async () => {
    const response: GridSubmitResponse = { gridId: 'new-grid-123' };
    mockGridPort.submitGrid.mockReturnValue(of(response));

    const request: SubmitGridRequest = {
      title: 'Test Grid',
      difficulty: 'EASY',
      width: 5,
      height: 5,
      cells: [],
    };

    const result = await useCase.execute(request);
    expect(result).toBe('new-grid-123');
    expect(mockGridPort.submitGrid).toHaveBeenCalledWith(request);
  });
});
