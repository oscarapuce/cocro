import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '@infrastructure/auth/auth.service';
import { getNetworkErrorMessage } from '@infrastructure/http/network-error';
import { ButtonComponent } from '@presentation/shared/components/button/button.component';
import { InputComponent } from '@presentation/shared/components/input/input.component';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, ButtonComponent, InputComponent],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private router = inject(Router);

  // Mémorisé avant le login pour afficher le banner post-connexion
  private readonly cameFromAnonymous = this.auth.isAnonymous();

  form = this.fb.nonNullable.group({
    username: ['', Validators.required],
    password: ['', Validators.required],
  });

  loading = signal(false);
  error = signal('');

  submit(): void {
    if (this.form.invalid) return;
    this.loading.set(true);
    this.error.set('');

    this.auth.login(this.form.getRawValue()).subscribe({
      next: () =>
        this.router.navigate(['/'], {
          state: { fromAnonymous: this.cameFromAnonymous },
        }),
      error: (err: unknown) => {
        this.error.set(getNetworkErrorMessage(err, 'Erreur serveur.'));
        this.loading.set(false);
      },
    });
  }
}
