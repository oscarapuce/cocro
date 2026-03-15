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
    path: 'room/:shareCode',
    loadComponent: () =>
      import('./room/lobby-room.component').then((m) => m.LobbyRoomComponent),
  },
  {
    path: '',
    redirectTo: 'create',
    pathMatch: 'full',
  },
];
