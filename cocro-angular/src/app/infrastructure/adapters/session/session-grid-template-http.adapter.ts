import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { SessionGridTemplatePort } from '@application/ports/session/session-grid-template.port';
import { GridTemplateResponse } from '@domain/models/grid-template.model';
import { environment } from '@infrastructure/environment';

@Injectable({ providedIn: 'root' })
export class SessionGridTemplateHttpAdapter implements SessionGridTemplatePort {
  private readonly baseUrl = `${environment.apiBaseUrl}/api/sessions`;

  constructor(private http: HttpClient) {}

  getGridTemplate(shareCode: string): Observable<GridTemplateResponse> {
    return this.http.get<GridTemplateResponse>(`${this.baseUrl}/${shareCode}/grid-template`);
  }
}
