import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { JoinSessionUseCase } from '@application/use-cases/join-session.use-case';
import { getNetworkErrorMessage } from '@application/error/error-message.util';
import { ButtonComponent } from '@presentation/shared/components/button/button.component';
import { InputComponent } from '@presentation/shared/components/input/input.component';

@Component({
  selector: 'app-join-session',
  standalone: true,
  imports: [ReactiveFormsModule, ButtonComponent, InputComponent],
  templateUrl: './join-session.component.html',
  styleUrl: './join-session.component.scss',
})
export class JoinSessionComponent {
  private fb = inject(FormBuilder);
  private joinSession = inject(JoinSessionUseCase);
  private router = inject(Router);

  form = this.fb.nonNullable.group({
    shareCode: ['', [Validators.required, Validators.minLength(4)]],
  });

  loading = signal(false);
  error = signal('');

  submit(): void {
    if (this.form.invalid) return;
    this.loading.set(true);
    this.error.set('');

    const code = this.form.controls.shareCode.value.trim().toUpperCase();
    this.joinSession.execute(code).subscribe({
      next: () => this.router.navigate(['/play', code]),
      error: (err: unknown) => {
        this.error.set(getNetworkErrorMessage(err, 'Impossible de rejoindre la session.'));
        this.loading.set(false);
      },
    });
  }
}

