import { Routes } from '@angular/router';

export const GRID_EDITOR_ROUTES: Routes = [
  {
    path: 'editor',
    loadComponent: () =>
      import('./editor/grid-editor.component').then((m) => m.GridEditorComponent),
  },
  {
    path: 'editor/:gridId',
    loadComponent: () =>
      import('./editor/grid-editor.component').then((m) => m.GridEditorComponent),
  },
  {
    path: 'list',
    loadComponent: () =>
      import('./grid-list/grid-list.component').then((m) => m.GridListComponent),
  },
  {
    path: '',
    redirectTo: 'list',
    pathMatch: 'full',
  },
];
