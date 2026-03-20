import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '@infrastructure/auth/auth.service';
import { getNetworkErrorMessage } from '@infrastructure/http/network-error';
import { GAME_SESSION_PORT } from '@application/ports/session/game-session.port';
import { ButtonComponent } from '@presentation/shared/components/button/button.component';
import { InputComponent } from '@presentation/shared/components/input/input.component';
import { LandingHomeShellComponent } from '@presentation/shared/shell/landing-home-shell.component';

@Component({
  selector: 'cocro-home',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, ButtonComponent, InputComponent, LandingHomeShellComponent],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
})
export class HomeComponent implements OnInit {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private sessionPort = inject(GAME_SESSION_PORT);
  private router = inject(Router);

  joinForm = this.fb.nonNullable.group({
    shareCode: ['', [Validators.required, Validators.minLength(4)]],
  });

  joinLoading = signal(false);
  joinError = signal('');
  fromAnonymousBanner = signal(false);

  readonly username = () => this.auth.currentUser()?.username ?? '';

  ngOnInit(): void {
    if (window.history.state?.['fromAnonymous']) {
      this.fromAnonymousBanner.set(true);
    }
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/']);
  }

  joinSession(): void {
    if (this.joinForm.invalid) return;
    this.joinLoading.set(true);
    this.joinError.set('');

    const { shareCode } = this.joinForm.getRawValue();
    this.sessionPort.joinSession({ shareCode }).subscribe({
      next: () => this.router.navigate(['/lobby/room', shareCode]),
      error: (err: unknown) => {
        this.joinError.set(getNetworkErrorMessage(err, 'Erreur serveur.'));
        this.joinLoading.set(false);
      },
    });
  }
}
