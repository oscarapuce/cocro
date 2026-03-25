import { Inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { AuthPort, AUTH_PORT } from '@application/ports/auth/auth.port';
import { GameSessionPort, GAME_SESSION_PORT } from '@application/ports/session/game-session.port';
import { SessionFullResponse } from '@domain/models/session.model';

@Injectable({ providedIn: 'root' })
export class JoinSessionUseCase {
  constructor(
    @Inject(AUTH_PORT) private authPort: AuthPort,
    @Inject(GAME_SESSION_PORT) private sessionPort: GameSessionPort,
  ) {}

  execute(shareCode: string): Observable<SessionFullResponse> {
    const doJoin = () => this.sessionPort.joinSession({ shareCode });
    if (this.authPort.isAuthenticated()) {
      return doJoin();
    }
    return this.authPort.createGuest().pipe(switchMap(() => doJoin()));
  }
}
