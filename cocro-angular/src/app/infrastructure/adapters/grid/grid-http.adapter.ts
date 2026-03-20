import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Grid } from '@domain/models/grid.model';
import { GridPort } from '@application/ports/grid/grid.port';
import { SubmitGridRequest, PatchGridRequest, GridSubmitResponse } from '@application/dto/grid.dto';
import { environment } from '@infrastructure/environment';

@Injectable({ providedIn: 'root' })
export class GridHttpAdapter implements GridPort {
  private readonly baseUrl = `${environment.apiBaseUrl}/api/grids`;

  constructor(private http: HttpClient) {}

  getGrid(gridId: string): Observable<Grid> {
    return this.http.get<Grid>(`${this.baseUrl}/${gridId}`);
  }

  submitGrid(request: SubmitGridRequest): Observable<GridSubmitResponse> {
    return this.http.post<GridSubmitResponse>(this.baseUrl, request);
  }

  patchGrid(request: PatchGridRequest): Observable<void> {
    return this.http.patch<void>(this.baseUrl, request);
  }
}
