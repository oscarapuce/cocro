import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { GridCardComponent } from './grid-card.component';
import { GridSummary } from '@domain/models/grid-summary.model';

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
    const title = el.querySelector('.grid-card__title');
    expect(title?.textContent).toContain('Grille du dimanche');
  });

  it('should display grid difficulty', () => {
    const el: HTMLElement = fixture.nativeElement;
    const badge = el.querySelector('.grid-card__badge');
    expect(badge?.textContent).toContain('HARD');
  });

  it('should display grid dimensions', () => {
    const el: HTMLElement = fixture.nativeElement;
    const meta = el.querySelector('.grid-card__meta');
    expect(meta?.textContent).toContain('15');
    expect(meta?.textContent).toContain('12');
  });

  it('should emit launchSession when launch button is clicked', () => {
    const spy = jest.fn();
    component.launchSession.subscribe(spy);

    const el: HTMLElement = fixture.nativeElement;
    const launchBtn = el.querySelector(
      'cocro-button[variant="primary"]',
    ) as HTMLElement;
    launchBtn.click();

    expect(spy).toHaveBeenCalledTimes(1);
  });

  it('should emit edit when edit button is clicked', () => {
    const spy = jest.fn();
    component.edit.subscribe(spy);

    const el: HTMLElement = fixture.nativeElement;
    const editBtn = el.querySelector(
      'cocro-button[variant="secondary"]',
    ) as HTMLElement;
    editBtn.click();

    expect(spy).toHaveBeenCalledTimes(1);
  });
});
