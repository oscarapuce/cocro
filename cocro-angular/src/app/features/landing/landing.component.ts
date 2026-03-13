import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { switchMap } from 'rxjs/operators';
import { AuthService } from '../../shared/services/auth.service';
import { SessionService } from '../../shared/services/session.service';
import { ButtonComponent } from '../../shared/components/button/button.component';
import { InputComponent } from '../../shared/components/input/input.component';

@Component({
  selector: 'app-landing',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, ButtonComponent, InputComponent],
  templateUrl: './landing.component.html',
  styleUrl: './landing.component.scss',
})
export class LandingComponent implements OnInit {
  private fb = inject(FormBuilder);
  readonly auth = inject(AuthService);
  private sessionService = inject(SessionService);
  private router = inject(Router);

  joinForm = this.fb.nonNullable.group({
    shareCode: ['', [Validators.required, Validators.minLength(4)]],
  });

  joinLoading = signal(false);
  joinError = signal('');

  ngOnInit(): void {
    // Utilisateur déjà connecté en tant que PLAYER/ADMIN → dashboard
    if (this.auth.isPlayer()) {
      this.router.navigate(['/home']);
    }
  }

  joinSession(): void {
    if (this.joinForm.invalid) return;
    this.joinLoading.set(true);
    this.joinError.set('');

    const { shareCode } = this.joinForm.getRawValue();

    const doJoin = () =>
      this.sessionService.joinSession({ shareCode });

    const afterJoin = {
      next: () => this.router.navigate(['/lobby/room', shareCode]),
      error: (err: { status: number }) => {
        const msg =
          err.status === 409
            ? 'Session pleine ou déjà rejoint.'
            : err.status === 404
              ? 'Code invalide.'
              : 'Erreur serveur.';
        this.joinError.set(msg);
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
