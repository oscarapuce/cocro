import { Routes } from '@angular/router';
import { playerGuard } from '@infrastructure/guards/player.guard';

export const EDITOR_ROUTES: Routes = [
  {
    path: 'create',
    title: 'Créer une grille',
    canActivate: [playerGuard],
    loadComponent: () =>
      import('./grid-editor/grid-editor.component').then((m) => m.GridEditorComponent),
  },
  {
    path: ':gridId/edit',
    title: 'Éditer une grille',
    canActivate: [playerGuard],
    loadComponent: () =>
      import('./grid-editor/grid-editor.component').then((m) => m.GridEditorComponent),
  },
  {
    path: 'mine',
    title: 'Mes grilles',
    canActivate: [playerGuard],
    loadComponent: () =>
      import('../my-grids/my-grids.component').then((m) => m.MyGridsComponent),
  },
  {
    path: '',
    redirectTo: 'mine',
    pathMatch: 'full',
  },
];
