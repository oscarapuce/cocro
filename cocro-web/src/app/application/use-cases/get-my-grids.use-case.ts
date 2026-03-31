import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { GRID_PORT } from '@application/ports/grid/grid.port';
import { GridSummary } from '@domain/models/grid-summary.model';

@Injectable({ providedIn: 'root' })
export class GetMyGridsUseCase {
  private readonly gridPort = inject(GRID_PORT);

  execute(): Observable<GridSummary[]> {
    return this.gridPort.getMyGrids();
  }
}
