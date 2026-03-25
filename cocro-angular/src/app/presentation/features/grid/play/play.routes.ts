import { Routes } from '@angular/router';

export const PLAY_ROUTES: Routes = [
  {
    path: ':shareCode',
    title: 'CoCro — Jouer',
    loadComponent: () =>
      import('./grid-player.component').then((m) => m.GridPlayerComponent),
  },
];
