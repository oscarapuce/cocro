import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { routes } from './root.routes';
import { jwtInterceptor } from '@infrastructure/auth/jwt.interceptor';
import { networkErrorInterceptor } from '@infrastructure/http/network-error.interceptor';
import { GRID_PORT } from '@application/ports/grid/grid.port';
import { GridHttpAdapter } from '@infrastructure/adapters/grid/grid-http.adapter';
import { GAME_SESSION_PORT } from '@application/ports/session/game-session.port';
import { GameSessionHttpAdapter } from '@infrastructure/adapters/session/game-session-http.adapter';
import { SESSION_SOCKET_PORT } from '@application/ports/session/session-socket.port';
import { SessionStompAdapter } from '@infrastructure/adapters/session/session-stomp.adapter';
import { AUTH_PORT } from '@application/ports/auth/auth.port';
import { AuthService } from '@infrastructure/auth/auth.service';

export const rootConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(withInterceptors([jwtInterceptor, networkErrorInterceptor])),
    { provide: GRID_PORT, useExisting: GridHttpAdapter },
    { provide: GAME_SESSION_PORT, useExisting: GameSessionHttpAdapter },
    { provide: SESSION_SOCKET_PORT, useExisting: SessionStompAdapter },
    { provide: AUTH_PORT, useExisting: AuthService },
  ],
};
