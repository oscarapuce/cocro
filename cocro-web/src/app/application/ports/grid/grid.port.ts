import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';
import { Grid } from '@domain/models/grid.model';
import { GridSummary } from '@domain/models/grid-summary.model';
import { SubmitGridRequest, PatchGridRequest, GridSubmitResponse } from '@application/dto/grid.dto';

export interface GridPort {
  getGrid(gridId: string): Observable<Grid>;
  getMyGrids(): Observable<GridSummary[]>;
  submitGrid(request: SubmitGridRequest): Observable<GridSubmitResponse>;
  patchGrid(request: PatchGridRequest): Observable<void>;
}

export const GRID_PORT = new InjectionToken<GridPort>('GRID_PORT');
