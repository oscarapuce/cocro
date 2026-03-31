import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { GAME_SESSION_PORT } from '@application/ports/session/game-session.port';
import { SessionFullResponse } from '@domain/models/session.model';

@Injectable({ providedIn: 'root' })
export class CreateSessionUseCase {
  private readonly sessionPort = inject(GAME_SESSION_PORT);

  execute(gridId: string): Observable<SessionFullResponse> {
    return this.sessionPort.createSession({ gridId }).pipe(
      switchMap((created) => this.sessionPort.joinSession({ shareCode: created.shareCode })),
    );
  }
}
