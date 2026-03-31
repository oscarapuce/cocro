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
    path: 'mine',
    title: 'Mes sessions',
    canActivate: [playerGuard],
    loadComponent: () =>
      import('./my-sessions/my-sessions.component').then((m) => m.MySessionsComponent),
  },
  {
    path: '',
    redirectTo: 'mine',
    pathMatch: 'full',
  },
];
