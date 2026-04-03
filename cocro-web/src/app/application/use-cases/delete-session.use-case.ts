import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { SESSION_MANAGEMENT_PORT } from '@application/ports/session/session-management.port';

@Injectable({ providedIn: 'root' })
export class DeleteSessionUseCase {
  private readonly sessionPort = inject(SESSION_MANAGEMENT_PORT);

  execute(shareCode: string): Observable<void> {
    return this.sessionPort.deleteSession(shareCode);
  }
}
