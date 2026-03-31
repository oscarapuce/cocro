import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '@infrastructure/auth/auth.service';
import { LandingHomeShellComponent } from '@presentation/shared/shell/landing-home-shell.component';
import { FrontPanelComponent } from '@presentation/shared/front-panel/front-panel.component';

@Component({
  selector: 'app-landing',
  standalone: true,
  imports: [LandingHomeShellComponent, FrontPanelComponent],
  templateUrl: './landing.component.html',
  styleUrl: './landing.component.scss',
})
export class LandingComponent {
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly heroSubtitle = (): string =>
    this.auth.isPlayer()
      ? 'Créez ou rejoignez une session — jusqu\u2019à 4 joueurs en temps réel.'
      : 'Rejoignez une session avec un code — jusqu\u2019à 4 joueurs en temps réel.';

  leaveGuest(): void {
    this.auth.logout();
    this.router.navigate(['/']);
  }
}
