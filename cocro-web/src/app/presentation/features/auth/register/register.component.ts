import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AUTH_PORT, AuthPort } from '@application/ports/auth/auth.port';
import { getNetworkErrorMessage } from '@application/error/error-message.util';
import { ButtonComponent } from '@presentation/shared/components/button/button.component';
import { InputComponent } from '@presentation/shared/components/input/input.component';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, ButtonComponent, InputComponent],
  templateUrl: './register.component.html',
  styleUrl: './register.component.scss',
})
export class RegisterComponent {
  private fb = inject(FormBuilder);
  private auth = inject<AuthPort>(AUTH_PORT);
  private router = inject(Router);

  form = this.fb.nonNullable.group({
    username: ['', [Validators.required, Validators.minLength(3)]],
    password: ['', [Validators.required, Validators.minLength(6)]],
    email: [''],
  });

  loading = signal(false);
  error = signal('');

  submit(): void {
    if (this.form.invalid) return;
    this.loading.set(true);
    this.error.set('');

    const { username, password, email } = this.form.getRawValue();
    this.auth
      .register({ username, password, email: email || undefined })
      .subscribe({
        next: () => this.router.navigate(['/']),
        error: (err: unknown) => {
          this.error.set(getNetworkErrorMessage(err, 'Erreur serveur.'));
          this.loading.set(false);
        },
      });
  }
}
