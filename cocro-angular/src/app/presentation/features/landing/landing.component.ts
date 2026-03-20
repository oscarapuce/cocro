import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { switchMap } from 'rxjs/operators';
import { AuthService } from '@infrastructure/auth/auth.service';
import { getNetworkErrorMessage } from '@infrastructure/http/network-error';
import { GAME_SESSION_PORT } from '@application/ports/session/game-session.port';
import { ButtonComponent } from '@presentation/shared/components/button/button.component';
import { InputComponent } from '@presentation/shared/components/input/input.component';
import { LandingHomeShellComponent } from '@presentation/shared/shell/landing-home-shell.component';

@Component({
  selector: 'app-landing',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, ButtonComponent, InputComponent, LandingHomeShellComponent],
  templateUrl: './landing.component.html',
  styleUrl: './landing.component.scss',
})
export class LandingComponent {
  private fb = inject(FormBuilder);
  readonly auth = inject(AuthService);
  private sessionPort = inject(GAME_SESSION_PORT);
  private router = inject(Router);

  joinForm = this.fb.nonNullable.group({
    shareCode: ['', [Validators.required, Validators.minLength(4)]],
  });

  joinLoading = signal(false);
  joinError = signal('');

  readonly heroSubtitle = (): string =>
    this.auth.isPlayer()
      ? 'Créez ou rejoignez une session — jusqu’à 4 joueurs en temps réel.'
      : 'Rejoignez une session avec un code — jusqu’à 4 joueurs en temps réel.';

  joinSession(): void {
    if (this.joinForm.invalid) return;
    this.joinLoading.set(true);
    this.joinError.set('');

    const { shareCode } = this.joinForm.getRawValue();

    const doJoin = () =>
      this.sessionPort.joinSession({ shareCode });

    const afterJoin = {
      next: () => this.router.navigate(['/lobby/room', shareCode]),
      error: (err: unknown) => {
        this.joinError.set(getNetworkErrorMessage(err, 'Erreur serveur.'));
        this.joinLoading.set(false);
      },
    };

    if (this.auth.isAuthenticated()) {
      doJoin().subscribe(afterJoin);
    } else {
      // Créer un compte invité puis rejoindre
      this.auth
        .createGuest()
        .pipe(switchMap(() => doJoin()))
        .subscribe(afterJoin);
    }
  }
}
