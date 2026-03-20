import { Routes } from '@angular/router';
import { authGuard } from '@infrastructure/guards/auth.guard';
import { playerGuard } from '@infrastructure/guards/player.guard';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('@presentation/features/landing/landing.component').then(m => m.LandingComponent),
    title: 'CoCro',
  },
  {
    path: 'auth',
    loadChildren: () => import('@presentation/features/auth/auth.routes').then(m => m.AUTH_ROUTES),
  },
  {
    path: 'home',
    redirectTo: '',
    pathMatch: 'full',
  },
  {
    path: 'grid',
    canActivate: [playerGuard],
    loadChildren: () => import('@presentation/features/grid/editor/editor.routes').then(m => m.EDITOR_ROUTES),
  },
  {
    path: 'lobby',
    canActivate: [authGuard],
    loadChildren: () => import('@presentation/features/lobby/lobby.routes').then(m => m.LOBBY_ROUTES),
  },
  {
    path: 'game',
    canActivate: [authGuard],
    loadChildren: () => import('@presentation/features/game/game.routes').then(m => m.GAME_ROUTES),
  },
  { path: '**', redirectTo: '' },
];
