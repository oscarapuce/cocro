import { Routes } from '@angular/router';
import { playerGuard } from '@infrastructure/guards/player.guard';

export const LOBBY_ROUTES: Routes = [
  {
    path: 'create',
    canActivate: [playerGuard],
    loadComponent: () =>
      import('./create/create-session.component').then((m) => m.CreateSessionComponent),
  },
  {
    path: '',
    redirectTo: 'create',
    pathMatch: 'full',
  },
];
