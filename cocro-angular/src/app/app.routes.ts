import { Routes } from '@angular/router';
import { authGuard } from '@infrastructure/guards/auth.guard';
import { playerGuard } from '@infrastructure/guards/player.guard';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./presentation/features/landing/landing.component').then((m) => m.LandingComponent),
  },
  {
    path: 'auth',
    loadChildren: () => import('./presentation/features/auth/auth.routes').then((m) => m.AUTH_ROUTES),
  },
  {
    path: 'home',
    canActivate: [playerGuard],
    loadComponent: () =>
      import('./presentation/features/home/home.component').then((m) => m.HomeComponent),
  },
  {
    path: 'grids',
    canActivate: [playerGuard],
    loadChildren: () =>
      import('./presentation/features/grid-editor/grid-editor.routes').then((m) => m.GRID_EDITOR_ROUTES),
  },
  {
    path: 'lobby',
    canActivate: [authGuard],
    loadChildren: () =>
      import('./presentation/features/lobby/lobby.routes').then((m) => m.LOBBY_ROUTES),
  },
  {
    path: 'game',
    canActivate: [authGuard],
    loadChildren: () =>
      import('./presentation/features/game/game.routes').then((m) => m.GAME_ROUTES),
  },
  {
    path: '**',
    redirectTo: '',
  },
];
