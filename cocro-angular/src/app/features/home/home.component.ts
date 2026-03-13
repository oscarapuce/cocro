import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../shared/services/auth.service';
import { SessionService } from '../../shared/services/session.service';
import { ButtonComponent } from '../../shared/components/button/button.component';
import { InputComponent } from '../../shared/components/input/input.component';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, ButtonComponent, InputComponent],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
})
export class HomeComponent implements OnInit {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private sessionService = inject(SessionService);
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
    this.sessionService.joinSession({ shareCode }).subscribe({
      next: () => this.router.navigate(['/lobby/room', shareCode]),
      error: (err) => {
        const msg =
          err.status === 409
            ? 'Session pleine ou déjà rejoint.'
            : err.status === 404
              ? 'Code invalide.'
              : 'Erreur serveur.';
        this.joinError.set(msg);
        this.joinLoading.set(false);
      },
    });
  }
}
