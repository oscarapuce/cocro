import { CanDeactivateFn } from '@angular/router';
import { GridPlayerComponent } from './grid-player.component';

/**
 * Ensures the player properly leaves the session when navigating away
 * from the game board via Angular router (in-app navigation).
 */
export const playLeaveGuard: CanDeactivateFn<GridPlayerComponent> = (component) => {
  if (component.connected()) {
    component.leave();
  }
  return true;
};

