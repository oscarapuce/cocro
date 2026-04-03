import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { GRID_PORT } from '@application/ports/grid/grid.port';

@Injectable({ providedIn: 'root' })
export class DeleteGridUseCase {
  private readonly gridPort = inject(GRID_PORT);

  execute(gridId: string): Observable<void> {
    return this.gridPort.deleteGrid(gridId);
  }
}

