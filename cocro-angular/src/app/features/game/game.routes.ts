import { Routes } from '@angular/router';

export const GAME_ROUTES: Routes = [
  {
    path: ':shareCode',
    loadComponent: () =>
      import('./board/game-board.component').then((m) => m.GameBoardComponent),
  },
];
