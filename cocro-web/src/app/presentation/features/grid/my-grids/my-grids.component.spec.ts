import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError, Subject } from 'rxjs';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { MyGridsComponent } from './my-grids.component';
import { GetMyGridsUseCase } from '@application/use-cases/get-my-grids.use-case';
import { CreateSessionUseCase } from '@application/use-cases/create-session.use-case';
import { DeleteGridUseCase } from '@application/use-cases/delete-grid.use-case';
import { ToastService } from '@presentation/shared/components/toast/toast.service';
import { GridSummary } from '@domain/models/grid-summary.model';

const GRID_STUB: GridSummary = {
  gridId: 'grid-1',
  title: 'Mots croisés #1',
  width: 10,
  height: 10,
  difficulty: 'EASY',
  createdAt: '2026-03-20T10:00:00Z',
  updatedAt: '2026-03-20T12:00:00Z',
};

describe('MyGridsComponent', () => {
  let component: MyGridsComponent;
  let fixture: ComponentFixture<MyGridsComponent>;
  let mockGetMyGrids: { execute: jest.Mock };
  let mockCreateSession: { execute: jest.Mock };
  let mockDeleteGrid: { execute: jest.Mock };
  let mockRouter: { navigate: jest.Mock };
  let mockToast: { error: jest.Mock; success: jest.Mock };

  function createComponent(): void {
    fixture = TestBed.createComponent(MyGridsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(async () => {
    mockGetMyGrids = { execute: jest.fn().mockReturnValue(of([])) };
    mockCreateSession = { execute: jest.fn() };
    mockDeleteGrid = { execute: jest.fn() };
    mockRouter = { navigate: jest.fn() };
    mockToast = { error: jest.fn(), success: jest.fn() };

    await TestBed.configureTestingModule({
      imports: [MyGridsComponent],
      providers: [
        { provide: GetMyGridsUseCase, useValue: mockGetMyGrids },
        { provide: CreateSessionUseCase, useValue: mockCreateSession },
        { provide: DeleteGridUseCase, useValue: mockDeleteGrid },
        { provide: Router, useValue: mockRouter },
        { provide: ToastService, useValue: mockToast },
      ],
      schemas: [NO_ERRORS_SCHEMA],
    })
      .overrideComponent(MyGridsComponent, {
        set: {
          imports: [],
          schemas: [NO_ERRORS_SCHEMA],
        },
      })
      .compileComponents();
  });

  it('should be created', () => {
    createComponent();
    expect(component).toBeTruthy();
  });

  it('should show loading state initially', () => {
    const grids$ = new Subject<GridSummary[]>();
    mockGetMyGrids.execute.mockReturnValue(grids$);

    fixture = TestBed.createComponent(MyGridsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.loading()).toBe(true);
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('.my-grids__state')).toBeTruthy();
  });

  it('should show grid cards when grids are loaded', () => {
    mockGetMyGrids.execute.mockReturnValue(of([GRID_STUB]));
    createComponent();

    expect(component.loading()).toBe(false);
    expect(component.grids()).toEqual([GRID_STUB]);
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('.my-grids__table')).toBeTruthy();
  });

  it('should show empty state when no grids', () => {
    mockGetMyGrids.execute.mockReturnValue(of([]));
    createComponent();

    expect(component.grids()).toEqual([]);
    expect(component.loading()).toBe(false);
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('.my-grids__state--empty')).toBeTruthy();
  });

  it('should show error state when load fails', () => {
    mockGetMyGrids.execute.mockReturnValue(
      throwError(() => new Error('network error')),
    );
    createComponent();

    expect(component.error()).toBe('Impossible de charger vos grilles.');
    expect(component.loading()).toBe(false);
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('.my-grids__state--error')).toBeTruthy();
  });

  it('should navigate to edit on onEdit()', () => {
    createComponent();
    component.onEdit(GRID_STUB);
    expect(mockRouter.navigate).toHaveBeenCalledWith([
      '/grid',
      'grid-1',
      'edit',
    ]);
  });

  it('should navigate to play on successful launch', () => {
    createComponent();
    const sessionResponse = { shareCode: 'ABC123' } as any;
    mockCreateSession.execute.mockReturnValue(of(sessionResponse));

    component.onLaunch(GRID_STUB);

    expect(mockCreateSession.execute).toHaveBeenCalledWith('grid-1');
    expect(mockRouter.navigate).toHaveBeenCalledWith(['/play', 'ABC123']);
  });

  it('should show toast error on launch failure', () => {
    createComponent();
    mockCreateSession.execute.mockReturnValue(
      throwError(() => new Error('fail')),
    );

    component.onLaunch(GRID_STUB);

    expect(mockToast.error).toHaveBeenCalledWith(
      'Impossible de lancer la session.',
    );
    expect(component.launching()).toBeNull();
  });

  it('should not launch if already launching', () => {
    createComponent();
    const session$ = new Subject<any>();
    mockCreateSession.execute.mockReturnValue(session$);

    component.onLaunch(GRID_STUB);
    expect(component.launching()).toBe('grid-1');

    // Second call while first is in-flight should be ignored
    component.onLaunch(GRID_STUB);
    expect(mockCreateSession.execute).toHaveBeenCalledTimes(1);
  });

  it('should reload grids on retry()', () => {
    mockGetMyGrids.execute.mockReturnValue(
      throwError(() => new Error('fail')),
    );
    createComponent();
    expect(component.error()).toBeTruthy();

    // Now make the retry succeed
    mockGetMyGrids.execute.mockReturnValue(of([GRID_STUB]));
    component.retry();

    expect(component.error()).toBe('');
    expect(component.grids()).toEqual([GRID_STUB]);
    expect(mockGetMyGrids.execute).toHaveBeenCalledTimes(2);
  });
});
