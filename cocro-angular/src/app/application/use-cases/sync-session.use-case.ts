import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { GAME_SESSION_PORT } from '@application/ports/session/game-session.port';
import { SessionFullResponse } from '@domain/models/session.model';

@Injectable({ providedIn: 'root' })
export class SyncSessionUseCase {
  private readonly sessionPort = inject(GAME_SESSION_PORT);

  execute(shareCode: string): Observable<SessionFullResponse> {
    return this.sessionPort.syncSession(shareCode);
  }
}
