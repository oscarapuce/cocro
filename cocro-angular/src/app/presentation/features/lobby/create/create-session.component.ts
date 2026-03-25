import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { GAME_SESSION_PORT } from '@application/ports/session/game-session.port';
import { getNetworkErrorMessage } from '@infrastructure/http/network-error';
import { ButtonComponent } from '@presentation/shared/components/button/button.component';
import { InputComponent } from '@presentation/shared/components/input/input.component';

@Component({
  selector: 'app-create-session',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, ButtonComponent, InputComponent],
  templateUrl: './create-session.component.html',
  styleUrl: './create-session.component.scss',
})
export class CreateSessionComponent {
  private fb = inject(FormBuilder);
  private sessionPort = inject(GAME_SESSION_PORT);
  private router = inject(Router);

  form = this.fb.nonNullable.group({
    gridId: ['', Validators.required],
  });

  loading = signal(false);
  error = signal('');

  submit(): void {
    if (this.form.invalid) return;
    this.loading.set(true);
    this.error.set('');

    this.sessionPort.createSession({ gridId: this.form.controls.gridId.value }).subscribe({
      next: (res) => this.router.navigate(['/play', res.shareCode]),
      error: (err: unknown) => {
        this.error.set(getNetworkErrorMessage(err, 'Impossible de créer la session.'));
        this.loading.set(false);
      },
    });
  }
}
