import { Component, inject, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { SessionHttpAdapter } from '@infrastructure/adapters/session/session-http.adapter';
import { SessionSummary } from '@domain/models/session-summary.model';
import { ToastService } from '@presentation/shared/components/toast/toast.service';
import { ButtonComponent } from '@presentation/shared/components/button/button.component';

@Component({
  selector: 'cocro-my-sessions',
  standalone: true,
  imports: [ButtonComponent],
  templateUrl: './my-sessions.component.html',
  styleUrl: './my-sessions.component.scss',
})
export class MySessionsComponent implements OnInit {
  private readonly sessionHttp = inject(SessionHttpAdapter);
  readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  readonly sessions = signal<SessionSummary[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly deleting = signal<string | null>(null);

  ngOnInit(): void {
    this.loadSessions();
  }

  private loadSessions(): void {
    this.loading.set(true);
    this.error.set('');
    this.sessionHttp.getMySessions().subscribe({
      next: (sessions) => {
        this.sessions.set(sessions);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger vos sessions.');
        this.loading.set(false);
      },
    });
  }

  get createdSessions(): SessionSummary[] {
    return this.sessions().filter((s) => s.role === 'CREATOR');
  }

  get joinedSessions(): SessionSummary[] {
    return this.sessions().filter((s) => s.role === 'PARTICIPANT');
  }

  onRejoin(session: SessionSummary): void {
    this.router.navigate(['/play', session.shareCode]);
  }

  onDelete(session: SessionSummary): void {
    if (this.deleting()) return;
    if (!confirm(`Supprimer la session "${session.gridTitle}" ?`)) return;
    this.deleting.set(session.shareCode);
    this.sessionHttp.deleteSession(session.shareCode).subscribe({
      next: () => {
        this.sessions.update((list) => list.filter((s) => s.shareCode !== session.shareCode));
        this.toast.success('Session supprimée.');
        this.deleting.set(null);
      },
      error: () => {
        this.toast.error('Impossible de supprimer la session.');
        this.deleting.set(null);
      },
    });
  }

  statusLabel(status: string): string {
    switch (status) {
      case 'PLAYING':
        return 'En cours';
      case 'INTERRUPTED':
        return 'Interrompue';
      case 'ENDED':
        return 'Terminée';
      default:
        return status;
    }
  }

  statusClass(status: string): string {
    return `session-card__status--${status.toLowerCase()}`;
  }

  canRejoin(session: SessionSummary): boolean {
    return session.status === 'PLAYING' || session.status === 'INTERRUPTED';
  }
}

