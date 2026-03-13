import { Routes } from '@angular/router';
import { authGuard } from './shared/guards/auth.guard';
import { playerGuard } from './shared/guards/player.guard';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./features/landing/landing.component').then((m) => m.LandingComponent),
  },
  {
    path: 'auth',
    loadChildren: () => import('./features/auth/auth.routes').then((m) => m.AUTH_ROUTES),
  },
  {
    path: 'home',
    canActivate: [playerGuard],
    loadComponent: () =>
      import('./features/home/home.component').then((m) => m.HomeComponent),
  },
  {
    path: 'grids',
    canActivate: [playerGuard],
    loadChildren: () =>
      import('./features/grid-editor/grid-editor.routes').then((m) => m.GRID_EDITOR_ROUTES),
  },
  {
    path: 'lobby',
    canActivate: [authGuard],
    loadChildren: () =>
      import('./features/lobby/lobby.routes').then((m) => m.LOBBY_ROUTES),
  },
  {
    path: 'game',
    canActivate: [authGuard],
    loadChildren: () =>
      import('./features/game/game.routes').then((m) => m.GAME_ROUTES),
  },
  {
    path: '**',
    redirectTo: '',
  },
];
