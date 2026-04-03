import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError, Subject } from 'rxjs';
import { signal } from '@angular/core';
import { GridPlayerComponent } from './grid-player.component';
import { AuthService } from '@infrastructure/auth/auth.service';
import { SESSION_SOCKET_PORT } from '@application/ports/session/session-socket.port';
import { JoinSessionUseCase } from '@application/use-cases/join-session.use-case';
import { SyncSessionUseCase } from '@application/use-cases/sync-session.use-case';
import { LeaveSessionUseCase } from '@application/use-cases/leave-session.use-case';
import { CheckGridUseCase } from '@application/use-cases/check-grid.use-case';
import { LetterAuthorService } from '@application/service/letter-author.service';
import { GridSelectorService } from '@application/service/grid-selector.service';
import { SessionEvent, GridUpdatedEvent, ParticipantJoinedEvent, ParticipantLeftEvent, SyncRequiredEvent } from '@domain/models/session-events.model';
import { SessionFullResponse } from '@domain/models/session.model';

describe('GridPlayerComponent', () => {
  let component: GridPlayerComponent;
  let fixture: ComponentFixture<GridPlayerComponent>;
  let eventCallback: ((event: SessionEvent) => void) | null;

  const mockRouter = { navigate: jest.fn() };
  const mockRoute = { snapshot: { paramMap: { get: () => 'TEST01' } } };
  const mockAuth = {
    token: signal('mock-jwt-token'),
    currentUser: signal({ userId: 'user-1', username: 'TestUser' }),
  };
  const mockAuthNoToken = {
    token: signal(null),
    currentUser: signal(null),
  };

  const mockSessionSocket = {
    connect: jest.fn((token: string, code: string, cb: (event: SessionEvent) => void) => {
      eventCallback = cb;
    }),
    disconnect: jest.fn(),
    sendGridUpdate: jest.fn(),
  };

  const fullDto: SessionFullResponse = {
    shareCode: 'TEST01',
    gridRevision: 5,
    participantCount: 2,
    status: 'PLAYING',
    participants: [
      { userId: 'user-1', username: 'TestUser', status: 'JOINED', isCreator: true },
      { userId: 'user-2', username: 'Other', status: 'JOINED', isCreator: false },
    ],
    cells: [{ x: 0, y: 0, letter: 'A' }],
    gridTemplate: {
      shortId: 'GRID01',
      title: 'Test',
      width: 5,
      height: 5,
      cells: [],
      difficulty: null,
      author: null,
      reference: null,
      description: null,
      globalClueLabel: null,
      globalClueWordLengths: null,
    },
  };

  const mockJoinSession = { execute: jest.fn().mockReturnValue(of(fullDto)) };
  const mockSyncSession = { execute: jest.fn().mockReturnValue(of(fullDto)) };
  const mockLeaveSession = { execute: jest.fn().mockReturnValue(of(void 0)) };
  const mockCheckGrid = { execute: jest.fn() };
  const mockLetterAuthors = {
    getAuthor: jest.fn(),
    setAuthor: jest.fn(),
    clearAuthor: jest.fn(),
    clearAll: jest.fn(),
  };
  const mockSelector = {
    grid: signal({ globalClue: null }),
    selectedX: signal(0),
    selectedY: signal(0),
    initGrid: jest.fn(),
    setLetterAt: jest.fn(),
    clearLetterAt: jest.fn(),
    clearAllLetters: jest.fn(),
    inputLetter: jest.fn(),
    moveRight: jest.fn(),
    moveLeft: jest.fn(),
    moveDown: jest.fn(),
    moveUp: jest.fn(),
    handleShift: jest.fn(),
  };

  function createComponent(auth = mockAuth) {
    eventCallback = null;
    TestBed.configureTestingModule({
      imports: [GridPlayerComponent],
      providers: [
        { provide: ActivatedRoute, useValue: mockRoute },
        { provide: Router, useValue: mockRouter },
        { provide: AuthService, useValue: auth },
        { provide: SESSION_SOCKET_PORT, useValue: mockSessionSocket },
        { provide: JoinSessionUseCase, useValue: mockJoinSession },
        { provide: SyncSessionUseCase, useValue: mockSyncSession },
        { provide: LeaveSessionUseCase, useValue: mockLeaveSession },
        { provide: CheckGridUseCase, useValue: mockCheckGrid },
        { provide: LetterAuthorService, useValue: mockLetterAuthors },
        { provide: GridSelectorService, useValue: mockSelector },
      ],
    }).overrideComponent(GridPlayerComponent, {
      set: { imports: [], template: '' },
    });
    fixture = TestBed.createComponent(GridPlayerComponent);
    component = fixture.componentInstance;
  }

  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('should redirect to login if no token', () => {
    createComponent(mockAuthNoToken as any);
    fixture.detectChanges();
    expect(mockRouter.navigate).toHaveBeenCalledWith(['/auth/login']);
    expect(mockJoinSession.execute).not.toHaveBeenCalled();
  });

  it('should load grid after successful join', () => {
    createComponent();
    fixture.detectChanges();
    expect(mockJoinSession.execute).toHaveBeenCalledWith('TEST01');
    expect(component.gridLoaded()).toBe(true);
    expect(component.loading()).toBe(false);
    expect(component.revision()).toBe(5);
    expect(component.participantCount()).toBe(2);
    expect(mockSelector.initGrid).toHaveBeenCalled();
    expect(mockSelector.setLetterAt).toHaveBeenCalledWith(0, 0, 'A');
    expect(mockSessionSocket.connect).toHaveBeenCalled();
  });

  it('should show error banner on join failure', () => {
    mockJoinSession.execute.mockReturnValueOnce(throwError(() => new Error('fail')));
    createComponent();
    fixture.detectChanges();
    expect(component.error()).toBeTruthy();
    expect(component.loading()).toBe(false);
  });

  it('should apply GridUpdated from other user', () => {
    createComponent();
    fixture.detectChanges();
    eventCallback!({
      type: 'GridUpdated',
      actorId: 'user-2',
      posX: 1,
      posY: 2,
      commandType: 'PLACE_LETTER',
      letter: 'B',
      revision: 6,
    } as GridUpdatedEvent);
    expect(mockSelector.setLetterAt).toHaveBeenCalledWith(1, 2, 'B');
    expect(component.revision()).toBe(6);
  });

  it('should ignore own GridUpdated', () => {
    createComponent();
    fixture.detectChanges();
    const prevRevision = component.revision();
    eventCallback!({
      type: 'GridUpdated',
      actorId: 'user-1',
      posX: 1,
      posY: 2,
      commandType: 'PLACE_LETTER',
      letter: 'C',
      revision: 6,
    } as GridUpdatedEvent);
    // revision should not change for own events
    expect(component.revision()).toBe(prevRevision);
  });

  it('should update participants on ParticipantJoined', () => {
    createComponent();
    fixture.detectChanges();
    eventCallback!({
      type: 'ParticipantJoined',
      userId: 'user-3',
      username: 'NewPlayer',
      participantCount: 3,
    } as ParticipantJoinedEvent);
    expect(component.participantCount()).toBe(3);
    expect(component.participants().find(p => p.userId === 'user-3')).toBeTruthy();
  });

  it('should update participants on ParticipantLeft', () => {
    createComponent();
    fixture.detectChanges();
    eventCallback!({
      type: 'ParticipantLeft',
      userId: 'user-2',
      username: 'Other',
      participantCount: 1,
      reason: 'explicit',
    } as ParticipantLeftEvent);
    expect(component.participantCount()).toBe(1);
    const left = component.participants().find(p => p.userId === 'user-2');
    expect(left?.status).toBe('LEFT');
  });

  it('should set status ENDED on SessionEnded', () => {
    createComponent();
    fixture.detectChanges();
    eventCallback!({ type: 'SessionEnded', shareCode: 'TEST01', correctCount: 10, totalCount: 10 });
    expect(component.status()).toBe('ENDED');
  });

  it('should trigger resync on SyncRequired', () => {
    createComponent();
    fixture.detectChanges();
    eventCallback!({ type: 'SyncRequired', currentRevision: 20 } as SyncRequiredEvent);
    expect(mockSyncSession.execute).toHaveBeenCalledWith('TEST01');
    expect(component.syncing()).toBe(false); // after subscribe completes
  });

  it('should leave and disconnect on ngOnDestroy', () => {
    createComponent();
    fixture.detectChanges();
    component.ngOnDestroy();
    expect(mockLeaveSession.execute).toHaveBeenCalledWith('TEST01');
    expect(mockSessionSocket.disconnect).toHaveBeenCalled();
  });

  it('should send grid update on keyboard letter input', () => {
    createComponent();
    fixture.detectChanges();
    const event = new KeyboardEvent('keydown', { key: 'a' });
    Object.defineProperty(event, 'target', { value: document.createElement('div') });
    component.onKeyDown(event);
    expect(mockSelector.inputLetter).toHaveBeenCalledWith('A');
    expect(mockSessionSocket.sendGridUpdate).toHaveBeenCalledWith('TEST01', expect.objectContaining({
      commandType: 'PLACE_LETTER',
      letter: 'A',
    }));
  });

  it('should trigger resync on revision gap detection', () => {
    createComponent();
    fixture.detectChanges();
    // Current revision is 5, send event with revision 10 (gap of 4)
    eventCallback!({
      type: 'GridUpdated',
      actorId: 'user-2',
      posX: 0,
      posY: 0,
      commandType: 'PLACE_LETTER',
      letter: 'Z',
      revision: 10,
    } as GridUpdatedEvent);
    // Should trigger resync instead of applying update
    expect(mockSyncSession.execute).toHaveBeenCalledWith('TEST01');
  });
});
