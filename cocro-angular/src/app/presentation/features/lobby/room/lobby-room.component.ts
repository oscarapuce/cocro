import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { GAME_SESSION_PORT } from '@application/ports/session/game-session.port';
import { AuthService } from '@infrastructure/auth/auth.service';
import { getNetworkErrorMessage } from '@infrastructure/http/network-error';
import { ButtonComponent } from '@presentation/shared/components/button/button.component';

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
  private sessionPort = inject(GAME_SESSION_PORT);
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
    this.sessionPort.startSession({ shareCode: this.shareCode() }).subscribe({
      next: () => this.router.navigate(['/game', this.shareCode()]),
      error: (err: unknown) => {
        this.error.set(getNetworkErrorMessage(err, 'Erreur.'));
        this.starting.set(false);
      },
    });
  }
}
