import { Inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { GridPort, GRID_PORT } from '@application/ports/grid/grid.port';
import { SubmitGridRequest, GridSubmitResponse } from '@application/dto/grid.dto';

@Injectable({ providedIn: 'root' })
export class SubmitGridUseCase {
  constructor(@Inject(GRID_PORT) private gridPort: GridPort) {}

  execute(request: SubmitGridRequest): Observable<GridSubmitResponse> {
    return this.gridPort.submitGrid(request);
  }
}
