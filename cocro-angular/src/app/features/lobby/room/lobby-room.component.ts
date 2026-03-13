import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { SessionService } from '../../../shared/services/session.service';
import { AuthService } from '../../../shared/services/auth.service';
import { ButtonComponent } from '../../../shared/components/button/button.component';

interface Participant {
  userId: string;
  label: string;
  online: boolean;
}

@Component({
  selector: 'app-lobby-room',
  standalone: true,
  imports: [RouterLink, ButtonComponent],
  templateUrl: './lobby-room.component.html',
  styleUrl: './lobby-room.component.scss',
})
export class LobbyRoomComponent implements OnInit {
  shareCode = signal('');
  participantCount = signal(1);
  starting = signal(false);
  error = signal('');

  // Minimal participant list: we only know the count from BFF for now
  participantSlots = computed(() =>
    Array.from({ length: 4 }, (_, i) => ({
      filled: i < this.participantCount(),
      label: i === 0 ? this.auth.currentUser()?.username ?? 'Vous' : `Joueur ${i + 1}`,
    })),
  );

  isCreator = signal(true); // assume creator if landing here after create

  private route = inject(ActivatedRoute);
  private sessionService = inject(SessionService);
  private auth = inject(AuthService);
  private router = inject(Router);

  ngOnInit(): void {
    const code = this.route.snapshot.paramMap.get('shareCode') ?? '';
    this.shareCode.set(code);
  }

  copyCode(): void {
    navigator.clipboard.writeText(this.shareCode());
  }

  startGame(): void {
    this.starting.set(true);
    this.sessionService.startSession({ shareCode: this.shareCode() }).subscribe({
      next: () => this.router.navigate(['/game', this.shareCode()]),
      error: (err) => {
        this.error.set(err.status === 400 ? 'Déjà démarré ou non autorisé.' : 'Erreur.');
        this.starting.set(false);
      },
    });
  }
}
