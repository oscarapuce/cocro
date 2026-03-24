import { Component, computed, HostListener, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '@infrastructure/auth/auth.service';
import { SESSION_SOCKET_PORT } from '@application/ports/session/session-socket.port';
import { GAME_SESSION_PORT } from '@application/ports/session/game-session.port';
import { SESSION_GRID_TEMPLATE_PORT } from '@application/ports/session/session-grid-template.port';
import { GridSelectorService } from '@application/service/grid-selector.service';
import { createEmptyGrid } from '@domain/services/grid-utils.service';
import { mapGridTemplateToGrid } from '@infrastructure/adapters/session/grid-template.mapper';
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
import { CardComponent } from '@presentation/shared/components/card/card.component';
import { GridComponent } from '@presentation/shared/grid/grid-wrapper/grid.component';
import { GlobalCluePreviewComponent } from '@presentation/features/grid/editor/global-clue-preview/global-clue-preview.component';
import { PlayHeaderComponent } from './play-header/play-header.component';
import { PlayInfoComponent } from './play-info/play-info.component';

@Component({
  selector: 'cocro-grid-player',
  standalone: true,
  imports: [
    CardComponent,
    GridComponent,
    GlobalCluePreviewComponent,
    PlayHeaderComponent,
    PlayInfoComponent,
  ],
  templateUrl: './grid-player.component.html',
  styleUrl: './grid-player.component.scss',
})
export class GridPlayerComponent implements OnInit, OnDestroy {
  readonly shareCode = signal('');
  readonly status = signal<SessionStatus>('CREATING');
  readonly participantCount = signal(0);
  readonly revision = signal(0);
  readonly connected = signal(false);
  readonly loading = signal(true);
  readonly gridLoaded = signal(false);
  readonly error = signal<string | null>(null);

  private readonly letterAuthors = signal(new Map<string, 'me' | string>());

  readonly getCellColorClass = (x: number, y: number): string => {
    const author = this.letterAuthors().get(`${x},${y}`);
    if (!author) return '';
    return author === 'me' ? 'letter--mine' : 'letter--other';
  };

  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);
  private readonly sessionSocket = inject(SESSION_SOCKET_PORT);
  private readonly gameSession = inject(GAME_SESSION_PORT);
  private readonly gridTemplatePort = inject(SESSION_GRID_TEMPLATE_PORT);
  readonly selector = inject(GridSelectorService);
  private readonly router = inject(Router);

  readonly myUserId = computed(() => this.auth.currentUser()?.userId ?? '');

  readonly hasGlobalClue = computed(() => !!this.selector.grid().globalClue?.label);

  ngOnInit(): void {
    const code = this.route.snapshot.paramMap.get('shareCode') ?? '';
    this.shareCode.set(code);

    const token = this.auth.token();
    if (!token) {
      this.router.navigate(['/auth/login']);
      return;
    }

    this.sessionSocket.connect(token, code, (event) => {
      this.connected.set(true);
      this.handleEvent(event);
    });
  }

  ngOnDestroy(): void {
    this.sessionSocket.disconnect();
    this.selector.initGrid(createEmptyGrid('0', '', 10, 10));
  }

  @HostListener('window:keydown', ['$event'])
  onKeyDown(event: KeyboardEvent): void {
    if (!this.gridLoaded() || this.loading()) return;

    const target = event.target as HTMLElement;
    const tag = target.tagName.toLowerCase();
    if (tag === 'input' || tag === 'textarea' || target.isContentEditable) return;

    const hasModifier =
      event.ctrlKey ||
      event.metaKey ||
      event.altKey ||
      event.getModifierState?.('AltGraph');
    if (
      hasModifier ||
      event.key === 'Control' ||
      event.key === 'Meta' ||
      event.key === 'Alt' ||
      event.key === 'AltGraph'
    ) return;

    const x = this.selector.selectedX();
    const y = this.selector.selectedY();

    switch (event.key) {
      case 'ArrowRight': this.selector.moveRight(); break;
      case 'ArrowLeft':  this.selector.moveLeft();  break;
      case 'ArrowDown':  this.selector.moveDown();  break;
      case 'ArrowUp':    this.selector.moveUp();    break;
      case 'Shift':
        this.selector.handleShift();
        break;
      case 'Backspace':
      case 'Delete':
        this.selector.clearLetterAt(x, y);
        this.sessionSocket.sendGridUpdate(this.shareCode(), { posX: x, posY: y, commandType: 'CLEAR_CELL' });
        this.letterAuthors.update(m => { const n = new Map(m); n.delete(`${x},${y}`); return n; });
        event.preventDefault();
        break;
      default:
        if (/^[a-zA-Z]$/.test(event.key)) {
          const letter = event.key.toUpperCase();
          this.selector.inputLetter(letter);
          this.sessionSocket.sendGridUpdate(this.shareCode(), { posX: x, posY: y, commandType: 'PLACE_LETTER', letter });
          this.letterAuthors.update(m => { const n = new Map(m); n.set(`${x},${y}`, 'me'); return n; });
          event.preventDefault();
        }
    }
  }

  leave(): void {
    this.gameSession.leaveSession({ shareCode: this.shareCode() }).subscribe();
    this.sessionSocket.disconnect();
    this.router.navigate(['/']);
  }

  private loadGridTemplate(): void {
    this.gridTemplatePort.getGridTemplate(this.shareCode()).subscribe({
      next: (template) => {
        this.selector.initGrid(mapGridTemplateToGrid(template));
        this.gridLoaded.set(true);
        this.loadCurrentState();
      },
      error: () => {
        this.error.set('Impossible de charger la grille. Veuillez réessayer.');
        this.loading.set(false);
      },
    });
  }

  private loadCurrentState(): void {
    this.gameSession.getState(this.shareCode()).subscribe({
      next: (state) => {
        this.revision.set(state.revision);
        state.cells.forEach((c: CellStateDto) => {
          if (c.letter) {
            this.selector.setLetterAt(c.x, c.y, String(c.letter));
          }
        });
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
      },
    });
  }

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
    this.loadGridTemplate();
  }

  private onGridUpdated(event: GridUpdatedEvent): void {
    if (event.actorId === this.myUserId()) return;
    this.revision.set(this.revision() + 1);
    if (event.commandType === 'PLACE_LETTER' && event.letter) {
      this.selector.setLetterAt(event.posX, event.posY, event.letter);
      this.letterAuthors.update(m => {
        const n = new Map(m);
        n.set(`${event.posX},${event.posY}`, event.actorId);
        return n;
      });
    } else if (event.commandType === 'CLEAR_CELL') {
      this.selector.clearLetterAt(event.posX, event.posY);
      this.letterAuthors.update(m => {
        const n = new Map(m);
        n.delete(`${event.posX},${event.posY}`);
        return n;
      });
    }
  }

  private resync(_targetRevision: number): void {
    this.letterAuthors.set(new Map());
    this.loadCurrentState();
  }
}
