import { Component, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '@infrastructure/auth/auth.service';
import { getNetworkErrorMessage } from '@infrastructure/http/network-error';
import { JoinSessionUseCase } from '@application/use-cases/join-session.use-case';
import { ButtonComponent } from '@presentation/shared/components/button/button.component';
import { InputComponent } from '@presentation/shared/components/input/input.component';

@Component({
  selector: 'cocro-front-panel',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, ButtonComponent, InputComponent],
  templateUrl: './front-panel.component.html',
  styleUrl: './front-panel.component.scss',
})
export class FrontPanelComponent {
  /** 'join' = panneau gauche clair, 'create' = panneau droit sombre */
  readonly side = input<'join' | 'create'>('join');

  readonly auth = inject(AuthService);
  private fb = inject(FormBuilder);
  private joinSessionUseCase = inject(JoinSessionUseCase);
  private router = inject(Router);

  joinForm = this.fb.nonNullable.group({
    shareCode: ['', [Validators.required, Validators.minLength(4)]],
  });

  joinLoading = signal(false);
  joinError = signal('');

  joinSession(): void {
    if (this.joinForm.invalid) return;
    this.joinLoading.set(true);
    this.joinError.set('');

    const { shareCode } = this.joinForm.getRawValue();
    this.joinSessionUseCase.execute(shareCode).subscribe({
      next: (fullDto) => this.router.navigate(['/play', fullDto.shareCode]),
      error: (err: unknown) => {
        this.joinError.set(getNetworkErrorMessage(err, 'Erreur serveur.'));
        this.joinLoading.set(false);
      },
    });
  }
}
