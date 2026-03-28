import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { GRID_PORT } from '@application/ports/grid/grid.port';
import { Grid } from '@domain/models/grid.model';

@Injectable({ providedIn: 'root' })
export class LoadGridUseCase {
  private readonly gridPort = inject(GRID_PORT);

  execute(gridId: string): Observable<Grid> {
    return this.gridPort.getGrid(gridId);
  }
}
