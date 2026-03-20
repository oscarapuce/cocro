import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { SubmitGridUseCase } from './submit-grid.use-case';
import { GridPort, GRID_PORT } from '@application/ports/grid/grid.port';
import { SubmitGridRequest, GridSubmitResponse } from '@application/dto/grid.dto';

describe('SubmitGridUseCase', () => {
  let useCase: SubmitGridUseCase;
  let mockGridPort: jest.Mocked<GridPort>;

  beforeEach(() => {
    mockGridPort = {
      getGrid: jest.fn(),
      submitGrid: jest.fn(),
      patchGrid: jest.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        SubmitGridUseCase,
        { provide: GRID_PORT, useValue: mockGridPort },
      ],
    });

    useCase = TestBed.inject(SubmitGridUseCase);
  });

  it('should be created', () => {
    expect(useCase).toBeTruthy();
  });

  it('should return the observable from gridPort.submitGrid', (done) => {
    const response: GridSubmitResponse = { gridId: 'grid-456' };
    mockGridPort.submitGrid.mockReturnValue(of(response));

    const request: SubmitGridRequest = {
      title: 'Another Grid',
      difficulty: 'MEDIUM',
      width: 10,
      height: 10,
      cells: [],
    };

    useCase.execute(request).subscribe((res) => {
      expect(res).toEqual(response);
      expect(mockGridPort.submitGrid).toHaveBeenCalledWith(request);
      done();
    });
  });
});
