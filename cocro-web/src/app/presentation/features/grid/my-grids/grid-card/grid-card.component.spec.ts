import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { GridCardComponent } from './grid-card.component';
import { GridSummary } from '@domain/models/grid-summary.model';
import { GRID_PORT } from '@application/ports/grid/grid.port';
import { of } from 'rxjs';

const GRID_STUB: GridSummary = {
  gridId: 'grid-42',
  title: 'Grille du dimanche',
  width: 15,
  height: 12,
  difficulty: 'HARD',
  createdAt: '2026-03-15T08:30:00Z',
  updatedAt: '2026-03-20T14:00:00Z',
};

describe('GridCardComponent', () => {
  let component: GridCardComponent;
  let fixture: ComponentFixture<GridCardComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [GridCardComponent],
      providers: [
        { provide: GRID_PORT, useValue: { getGrid: jest.fn().mockReturnValue(of(null)) } },
      ],
      schemas: [NO_ERRORS_SCHEMA],
    }).compileComponents();

    fixture = TestBed.createComponent(GridCardComponent);
    component = fixture.componentInstance;
    component.grid = GRID_STUB;
    fixture.detectChanges();
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
  });

  it('should display grid title', () => {
    const el: HTMLElement = fixture.nativeElement;
    const title = el.querySelector('.grid-row__title');
    expect(title?.textContent).toContain('Grille du dimanche');
  });

  it('should display grid dimensions', () => {
    const el: HTMLElement = fixture.nativeElement;
    const dims = el.querySelector('.grid-row__cell--dims');
    expect(dims?.textContent).toContain('15');
    expect(dims?.textContent).toContain('12');
  });

  it('should emit launchSession when launch button is clicked', () => {
    const spy = jest.fn();
    component.launchSession.subscribe(spy);

    const el: HTMLElement = fixture.nativeElement;
    const launchBtn = el.querySelector('.grid-row__btn--launch') as HTMLElement;
    launchBtn.click();

    expect(spy).toHaveBeenCalledTimes(1);
  });

  it('should emit edit when edit button is clicked', () => {
    const spy = jest.fn();
    component.edit.subscribe(spy);

    const el: HTMLElement = fixture.nativeElement;
    const editBtn = el.querySelector('.grid-row__btn--edit') as HTMLElement;
    editBtn.click();

    expect(spy).toHaveBeenCalledTimes(1);
  });

  it('should emit deleteGrid when delete button is clicked', () => {
    const spy = jest.fn();
    component.deleteGrid.subscribe(spy);

    const el: HTMLElement = fixture.nativeElement;
    const deleteBtn = el.querySelector('.grid-row__btn--delete') as HTMLElement;
    deleteBtn.click();

    expect(spy).toHaveBeenCalledTimes(1);
  });
});
