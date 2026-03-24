import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';
import { GridTemplateResponse } from '@domain/models/grid-template.model';

export interface SessionGridTemplatePort {
  getGridTemplate(shareCode: string): Observable<GridTemplateResponse>;
}

export const SESSION_GRID_TEMPLATE_PORT =
  new InjectionToken<SessionGridTemplatePort>('SESSION_GRID_TEMPLATE_PORT');
