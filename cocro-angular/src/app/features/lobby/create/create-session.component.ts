import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { SessionService } from '../../../shared/services/session.service';
import { ButtonComponent } from '../../../shared/components/button/button.component';
import { InputComponent } from '../../../shared/components/input/input.component';

@Component({
  selector: 'app-create-session',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, ButtonComponent, InputComponent],
  templateUrl: './create-session.component.html',
  styleUrl: './create-session.component.scss',
})
export class CreateSessionComponent {
  private fb = inject(FormBuilder);
  private sessionService = inject(SessionService);
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

    this.sessionService.createSession({ gridId: this.form.controls.gridId.value }).subscribe({
      next: (res) => this.router.navigate(['/lobby/room', res.shareCode]),
      error: () => {
        this.error.set('Impossible de créer la session.');
        this.loading.set(false);
      },
    });
  }
}
