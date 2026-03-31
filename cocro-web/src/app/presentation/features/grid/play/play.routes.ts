import { Routes } from '@angular/router';
import { playLeaveGuard } from './play-leave.guard';

export const PLAY_ROUTES: Routes = [
  {
    path: ':shareCode',
    title: 'CoCro — Jouer',
    canDeactivate: [playLeaveGuard],
    loadComponent: () =>
      import('./grid-player.component').then((m) => m.GridPlayerComponent),
  },
];
