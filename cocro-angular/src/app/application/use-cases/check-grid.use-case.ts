import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { GAME_SESSION_PORT } from '@application/ports/session/game-session.port';
import { GridCheckResponse } from '@domain/models/session.model';

@Injectable({ providedIn: 'root' })
export class CheckGridUseCase {
  private readonly sessionPort = inject(GAME_SESSION_PORT);

  execute(shareCode: string): Observable<GridCheckResponse> {
    return this.sessionPort.checkGrid(shareCode);
  }
}
