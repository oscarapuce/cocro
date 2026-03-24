import { Routes } from '@angular/router';
import { SESSION_GRID_TEMPLATE_PORT } from '@application/ports/session/session-grid-template.port';
import { SessionGridTemplateHttpAdapter } from '@infrastructure/adapters/session/session-grid-template-http.adapter';

export const PLAY_ROUTES: Routes = [
  {
    path: ':shareCode',
    title: 'CoCro — Jouer',
    providers: [
      { provide: SESSION_GRID_TEMPLATE_PORT, useClass: SessionGridTemplateHttpAdapter },
    ],
    loadComponent: () =>
      import('./grid-player.component').then((m) => m.GridPlayerComponent),
  },
];
