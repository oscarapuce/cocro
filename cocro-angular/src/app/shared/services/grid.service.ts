import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { GridSubmitResponse, PatchGridRequest, SubmitGridRequest } from '../models/grid.models';

@Injectable({ providedIn: 'root' })
export class GridService {
  private readonly base = `${environment.apiBaseUrl}/api/grids`;

  constructor(private http: HttpClient) {}

  submitGrid(dto: SubmitGridRequest): Observable<GridSubmitResponse> {
    return this.http.post<GridSubmitResponse>(this.base, dto);
  }

  patchGrid(dto: PatchGridRequest): Observable<void> {
    return this.http.patch<void>(this.base, dto);
  }
}
