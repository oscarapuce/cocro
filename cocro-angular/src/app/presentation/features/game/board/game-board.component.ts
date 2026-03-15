import { Component, OnInit, OnDestroy, inject, signal, computed } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '@infrastructure/auth/auth.service';
import { SessionService } from '@infrastructure/adapters/session.service';
import { StompService } from '@infrastructure/adapters/stomp.service';
import {
  GridUpdatedEvent,
  ParticipantJoinedEvent,
  ParticipantLeftEvent,
  SessionEvent,
  SessionStartedEvent,
  SessionWelcomeEvent,
  SyncRequiredEvent,
} from '@domain/models/session-events.model';
import { CellStateDto, SessionStatus } from '@domain/models/session.model';
import { ButtonComponent } from '@presentation/shared/components/button/button.component';

interface GridCell {
  x: number;
  y: number;
  letter: string;
  /** Who placed it last: 'me' | userId | '' */
  actorId: string;
}

@Component({
  selector: 'app-game-board',
  standalone: true,
  imports: [ButtonComponent],
  templateUrl: './game-board.component.html',
  styleUrl: './game-board.component.scss',
})
export class GameBoardComponent implements OnInit, OnDestroy {
  shareCode = signal('');
  status = signal<SessionStatus>('CREATING');
  participantCount = signal(0);
  revision = signal(0);
  // Grid state: map key = "x,y"
  private cellMap = signal(new Map<string, GridCell>());

  // Selected cell
  selectedX = signal(-1);
  selectedY = signal(-1);

  // Grid dimensions (inferred from cells or BFF)
  gridWidth = signal(10);
  gridHeight = signal(10);

  // Rows for template
  rows = computed(() => {
    const rows: GridCell[][] = [];
    for (let y = 0; y < this.gridHeight(); y++) {
      const row: GridCell[] = [];
      for (let x = 0; x < this.gridWidth(); x++) {
        const key = `${x},${y}`;
        row.push(this.cellMap().get(key) ?? { x, y, letter: '', actorId: '' });
      }
      rows.push(row);
    }
    return rows;
  });

  private route = inject(ActivatedRoute);
  private auth = inject(AuthService);
  private sessionService = inject(SessionService);
  private stomp = inject(StompService);
  private router = inject(Router);

  myUserId = computed(() => this.auth.currentUser()?.userId ?? '');
  connected = this.stomp.connected;

  ngOnInit(): void {
    const code = this.route.snapshot.paramMap.get('shareCode') ?? '';
    this.shareCode.set(code);

    const token = this.auth.token();
    if (!token) {
      this.router.navigate(['/auth/login']);
      return;
    }

    this.stomp.connect(token, code, (event) => this.handleEvent(event));
  }

  ngOnDestroy(): void {
    this.stomp.disconnect();
  }

  selectCell(x: number, y: number): void {
    this.selectedX.set(x);
    this.selectedY.set(y);
  }

  isSelected(x: number, y: number): boolean {
    return this.selectedX() === x && this.selectedY() === y;
  }

  onKeyDown(event: KeyboardEvent): void {
    const x = this.selectedX();
    const y = this.selectedY();
    if (x < 0) return;

    if (event.key.length === 1 && /[a-zA-ZÀ-ÿ]/.test(event.key)) {
      const letter = event.key.toUpperCase();
      this.placeLocalLetter(x, y, letter);
      this.stomp.sendGridUpdate(this.shareCode(), {
        posX: x,
        posY: y,
        commandType: 'PLACE_LETTER',
        letter,
      });
      // Move right
      if (x + 1 < this.gridWidth()) this.selectedX.set(x + 1);
    } else if (event.key === 'Backspace' || event.key === 'Delete') {
      this.placeLocalLetter(x, y, '');
      this.stomp.sendGridUpdate(this.shareCode(), {
        posX: x,
        posY: y,
        commandType: 'CLEAR_CELL',
      });
    } else if (event.key === 'ArrowRight') this.selectedX.set(Math.min(x + 1, this.gridWidth() - 1));
    else if (event.key === 'ArrowLeft') this.selectedX.set(Math.max(x - 1, 0));
    else if (event.key === 'ArrowDown') this.selectedY.set(Math.min(y + 1, this.gridHeight() - 1));
    else if (event.key === 'ArrowUp') this.selectedY.set(Math.max(y - 1, 0));
  }

  leave(): void {
    this.sessionService.leaveSession({ shareCode: this.shareCode() }).subscribe();
    this.stomp.disconnect();
    this.router.navigate(['/home']);
  }

  // — Event handlers —

  private handleEvent(event: SessionEvent): void {
    switch (event.type) {
      case 'SessionWelcome':
        this.onWelcome(event as SessionWelcomeEvent);
        break;
      case 'GridUpdated':
        this.onGridUpdated(event as GridUpdatedEvent);
        break;
      case 'ParticipantJoined':
        this.participantCount.set((event as ParticipantJoinedEvent).participantCount);
        break;
      case 'ParticipantLeft':
        this.participantCount.set((event as ParticipantLeftEvent).participantCount);
        break;
      case 'SessionStarted':
        this.status.set('PLAYING');
        this.participantCount.set((event as SessionStartedEvent).participantCount);
        break;
      case 'SyncRequired':
        this.resync((event as SyncRequiredEvent).currentRevision);
        break;
    }
  }

  private onWelcome(event: SessionWelcomeEvent): void {
    this.status.set(event.status);
    this.participantCount.set(event.participantCount);
    this.revision.set(event.gridRevision);
    // Load full state
    this.resync(event.gridRevision);
  }

  private onGridUpdated(event: GridUpdatedEvent): void {
    if (event.actorId === this.myUserId()) return; // already applied locally
    this.revision.set(this.revision() + 1);
    this.placeLocalLetter(event.posX, event.posY, event.letter ?? '', event.actorId);
  }

  private resync(targetRevision: number): void {
    this.sessionService.getState(this.shareCode()).subscribe((state) => {
      this.revision.set(state.revision);
      const map = new Map<string, GridCell>();
      state.cells.forEach((c: CellStateDto) => {
        map.set(`${c.x},${c.y}`, { x: c.x, y: c.y, letter: c.letter, actorId: '' });
      });
      this.cellMap.set(map);
    });
  }

  private placeLocalLetter(x: number, y: number, letter: string, actorId = 'me'): void {
    const key = `${x},${y}`;
    const current = new Map(this.cellMap());
    current.set(key, { x, y, letter, actorId });
    this.cellMap.set(current);
  }
}
