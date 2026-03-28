import { Component, computed, HostListener, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '@infrastructure/auth/auth.service';
import { SESSION_SOCKET_PORT } from '@application/ports/session/session-socket.port';
import { JoinSessionUseCase } from '@application/use-cases/join-session.use-case';
import { SyncSessionUseCase } from '@application/use-cases/sync-session.use-case';
import { LeaveSessionUseCase } from '@application/use-cases/leave-session.use-case';
import { CheckGridUseCase } from '@application/use-cases/check-grid.use-case';
import { LetterAuthorService } from '@application/service/letter-author.service';
import { GridSelectorService } from '@application/service/grid-selector.service';
import { createEmptyGrid } from '@domain/services/grid-utils.service';
import { mapGridTemplateToGrid } from '@infrastructure/adapters/session/grid-template.mapper';
import { getNetworkErrorMessage } from '@infrastructure/http/network-error';
import {
  GridCheckedEvent,
  GridUpdatedEvent,
  ParticipantJoinedEvent,
  ParticipantLeftEvent,
  SessionEndedEvent,
  SessionInterruptedEvent,
  SessionEvent,
  SessionWelcomeEvent,
  SyncRequiredEvent,
} from '@domain/models/session-events.model';
import { CellStateDto, GridCheckResponse, SessionFullResponse, SessionStatus } from '@domain/models/session.model';
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
  readonly status = signal<SessionStatus>('PLAYING');
  readonly participantCount = signal(0);
  readonly revision = signal(0);
  readonly connected = signal(false);
  readonly loading = signal(true);
  readonly gridLoaded = signal(false);
  readonly error = signal<string | null>(null);
  readonly checkResult = signal<GridCheckedEvent | null>(null);

  private readonly letterAuthors = inject(LetterAuthorService);

  readonly getCellColorClass = (x: number, y: number): string => {
    const author = this.letterAuthors.getAuthor(x, y);
    if (!author) return '';
    return author === 'me' ? 'letter--mine' : 'letter--other';
  };

  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);
  private readonly sessionSocket = inject(SESSION_SOCKET_PORT);
  private readonly joinSession = inject(JoinSessionUseCase);
  private readonly syncSession = inject(SyncSessionUseCase);
  private readonly leaveSession = inject(LeaveSessionUseCase);
  private readonly checkGridUseCase = inject(CheckGridUseCase);
  readonly selector = inject(GridSelectorService);
  private readonly router = inject(Router);

  readonly myUserId = computed(() => this.auth.currentUser()?.userId ?? '');

  readonly hasGlobalClue = computed(() => !!this.selector.grid().globalClue?.label);

  ngOnInit(): void {
    const shareCode = this.route.snapshot.paramMap.get('shareCode') ?? '';
    this.shareCode.set(shareCode);

    const token = this.auth.token();
    if (!token) {
      this.router.navigate(['/auth/login']);
      return;
    }

    this.joinSession.execute(shareCode).subscribe({
      next: (fullDto: SessionFullResponse) => {
        this.selector.initGrid(mapGridTemplateToGrid(fullDto.gridTemplate));
        fullDto.cells.forEach((c: CellStateDto) => {
          if (c.letter) this.selector.setLetterAt(c.x, c.y, c.letter);
        });
        this.revision.set(fullDto.gridRevision);
        this.participantCount.set(fullDto.participantCount);
        this.status.set(fullDto.status);
        this.gridLoaded.set(true);
        this.loading.set(false);

        this.sessionSocket.connect(token, shareCode, (event) => {
          this.connected.set(true);
          this.handleEvent(event);
        });
      },
      error: (err: unknown) => {
        this.error.set(getNetworkErrorMessage(err, 'Impossible de rejoindre la session.'));
        this.loading.set(false);
      },
    });
  }

  ngOnDestroy(): void {
    this.sessionSocket.disconnect();
    this.selector.initGrid(createEmptyGrid('0', '', 10, 10));
    this.letterAuthors.clearAll();
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
        this.letterAuthors.clearAuthor(x, y);
        event.preventDefault();
        break;
      default:
        if (/^[a-zA-Z]$/.test(event.key)) {
          const letter = event.key.toUpperCase();
          this.selector.inputLetter(letter);
          this.sessionSocket.sendGridUpdate(this.shareCode(), { posX: x, posY: y, commandType: 'PLACE_LETTER', letter });
          this.letterAuthors.setAuthor(x, y, 'me');
          event.preventDefault();
        }
    }
  }

  leave(): void {
    this.leaveSession.execute(this.shareCode()).subscribe();
    this.sessionSocket.disconnect();
    this.router.navigate(['/']);
  }

  checkGrid(): void {
    this.checkGridUseCase.execute(this.shareCode()).subscribe({
      next: (result: GridCheckResponse) => {
        this.checkResult.set({
          type: 'GridChecked',
          userId: this.myUserId(),
          isComplete: result.isComplete,
          correctCount: result.correctCount,
          totalCount: result.totalCount,
        } as GridCheckedEvent);
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
      case 'GridChecked':
        this.checkResult.set(event as GridCheckedEvent);
        break;
      case 'SessionEnded':
        this.status.set('ENDED');
        break;
      case 'SessionInterrupted':
        this.status.set('INTERRUPTED');
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
  }

  private onGridUpdated(event: GridUpdatedEvent): void {
    if (event.actorId === this.myUserId()) return;
    this.revision.set(this.revision() + 1);
    if (event.commandType === 'PLACE_LETTER' && event.letter) {
      this.selector.setLetterAt(event.posX, event.posY, event.letter);
      this.letterAuthors.setAuthor(event.posX, event.posY, event.actorId);
    } else if (event.commandType === 'CLEAR_CELL') {
      this.selector.clearLetterAt(event.posX, event.posY);
      this.letterAuthors.clearAuthor(event.posX, event.posY);
    }
  }

  private resync(_targetRevision: number): void {
    this.letterAuthors.clearAll();
    this.syncSession.execute(this.shareCode()).subscribe({
      next: (full: SessionFullResponse) => {
        this.revision.set(full.gridRevision);
        this.participantCount.set(full.participantCount);
        this.status.set(full.status);
        this.selector.clearAllLetters();
        full.cells.forEach((c: CellStateDto) => {
          if (c.letter) this.selector.setLetterAt(c.x, c.y, c.letter);
        });
      },
    });
  }
}
