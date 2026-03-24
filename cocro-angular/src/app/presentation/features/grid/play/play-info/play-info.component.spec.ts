import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PlayInfoComponent } from './play-info.component';
import { GridSelectorService } from '@application/service/grid-selector.service';
import { createEmptyGrid } from '@domain/services/grid-utils.service';
import { Cell, Grid } from '@domain/models/grid.model';

function makeGrid(overrides: Partial<Grid> = {}): Grid {
  return {
    ...createEmptyGrid('0', 'Test', 5, 5),
    ...overrides,
  };
}

describe('PlayInfoComponent', () => {
  let fixture: ComponentFixture<PlayInfoComponent>;
  let selector: GridSelectorService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [PlayInfoComponent],
    });
    fixture = TestBed.createComponent(PlayInfoComponent);
    selector = TestBed.inject(GridSelectorService);
    selector.initGrid(makeGrid());
    fixture.detectChanges();
  });

  it('shows hint when no description, no global clue, and no clue cell selected', () => {
    const hint = fixture.nativeElement.querySelector('.play-info__hint');
    expect(hint).not.toBeNull();
  });

  it('shows description when grid has one', () => {
    selector.initGrid(makeGrid({ description: 'Une grille thématique' }));
    fixture.detectChanges();
    const desc = fixture.nativeElement.querySelector('.play-info__description');
    expect(desc?.textContent?.trim()).toBe('Une grille thématique');
  });

  it('shows global clue label and word lengths', () => {
    selector.initGrid(makeGrid({
      globalClue: { label: 'Énigme mystère', wordLengths: [4, 3] },
    }));
    fixture.detectChanges();
    const label = fixture.nativeElement.querySelector('.play-info__enigme-label');
    const lengths = fixture.nativeElement.querySelector('.play-info__enigme-lengths');
    expect(label?.textContent?.trim()).toBe('Énigme mystère');
    expect(lengths?.textContent).toContain('4 + 3');
  });

  it('shows selected clue cell content when a clue cell is focused', () => {
    const clueCell: Cell = {
      x: 0,
      y: 0,
      type: 'CLUE_SINGLE',
      clues: [{ direction: 'RIGHT', text: 'Animal marin' }],
    };
    selector.initGrid({ ...makeGrid(), cells: [clueCell], width: 5, height: 5 });
    selector.selectedX.set(0);
    selector.selectedY.set(0);
    fixture.detectChanges();

    const clueText = fixture.nativeElement.querySelector('.play-info__clue-text');
    expect(clueText?.textContent?.trim()).toBe('Animal marin');
  });

  it('does not show clue section when a letter cell is selected', () => {
    const letterCell: Cell = { x: 1, y: 0, type: 'LETTER', letter: { value: '', separator: 'NONE' } };
    selector.initGrid({ ...makeGrid(), cells: [letterCell], width: 5, height: 5 });
    selector.selectedX.set(1);
    selector.selectedY.set(0);
    fixture.detectChanges();

    const clueSection = fixture.nativeElement.querySelector('.play-info__section--clue');
    expect(clueSection).toBeNull();
  });
});
