import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { GAME_SESSION_PORT } from '@application/ports/session/game-session.port';
import { SessionLeaveResponse } from '@domain/models/session.model';

@Injectable({ providedIn: 'root' })
export class LeaveSessionUseCase {
  private readonly sessionPort = inject(GAME_SESSION_PORT);

  execute(shareCode: string): Observable<SessionLeaveResponse> {
    return this.sessionPort.leaveSession({ shareCode });
  }
}
