import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { AuthPort, AUTH_PORT } from '@application/ports/auth/auth.port';
import { GameSessionPort, GAME_SESSION_PORT } from '@application/ports/session/game-session.port';
import { SessionFullResponse } from '@domain/models/session.model';

@Injectable({ providedIn: 'root' })
export class JoinSessionUseCase {
  private readonly authPort = inject(AUTH_PORT);
  private readonly sessionPort = inject(GAME_SESSION_PORT);

  execute(shareCode: string): Observable<SessionFullResponse> {
    const doJoin = () => this.sessionPort.joinSession({ shareCode });
    if (this.authPort.isAuthenticated()) {
      return doJoin();
    }
    return this.authPort.createGuest().pipe(switchMap(() => doJoin()));
  }
}
