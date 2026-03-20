import { Routes } from '@angular/router';

import {playerGuard} from '@infrastructure/guards/player.guard';

export const EDITOR_ROUTES: Routes = [
  {
    path: 'create',
    title: 'Grid edition',
    canActivate: [playerGuard],
    loadComponent: () =>
      import('./grid-editor/grid-editor.component').then((m) => m.GridEditorComponent),
  },
  {
    path: '',
    redirectTo: 'create',
    pathMatch: 'full',
  },
];
