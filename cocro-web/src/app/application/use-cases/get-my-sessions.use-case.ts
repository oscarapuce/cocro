import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { SESSION_MANAGEMENT_PORT } from '@application/ports/session/session-management.port';
import { SessionSummary } from '@domain/models/session-summary.model';

@Injectable({ providedIn: 'root' })
export class GetMySessionsUseCase {
  private readonly sessionPort = inject(SESSION_MANAGEMENT_PORT);

  execute(): Observable<SessionSummary[]> {
    return this.sessionPort.getMySessions();
  }
}
