import { Routes } from '@angular/router';
import { authGuard } from '@infrastructure/guards/auth.guard';
import { playerGuard } from '@infrastructure/guards/player.guard';

export const routes: Routes = [
  {
    path: '',
    data: { showSidebar: true },
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
    data: { showSidebar: true },
    canActivate: [playerGuard],
    loadChildren: () => import('@presentation/features/grid/editor/editor.routes').then(m => m.EDITOR_ROUTES),
  },
  {
    path: 'lobby',
    data: { showSidebar: true },
    canActivate: [authGuard],
    loadChildren: () => import('@presentation/features/lobby/lobby.routes').then(m => m.LOBBY_ROUTES),
  },
  {
    path: 'play',
    data: { showSidebar: true },
    canActivate: [authGuard],
    loadChildren: () => import('@presentation/features/grid/play/play.routes').then(m => m.PLAY_ROUTES),
  },

  { path: '**', redirectTo: '' },
];
