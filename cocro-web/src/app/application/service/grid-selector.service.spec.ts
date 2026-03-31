import { TestBed } from '@angular/core/testing';
import { GridSelectorService } from './grid-selector.service';
import { Grid } from '@domain/models/grid.model';
import { createEmptyGrid, getCell, withUpdatedCell } from '@domain/services/grid-utils.service';

describe('GridSelectorService', () => {
  let service: GridSelectorService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(GridSelectorService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('initGrid', () => {
    it('should set the grid signal', () => {
      const grid = createEmptyGrid('g1', 'My Grid', 5, 5);
      service.initGrid(grid);
      expect(service.grid().id).toBe('g1');
      expect(service.grid().title).toBe('My Grid');
      expect(service.grid().width).toBe(5);
      expect(service.grid().height).toBe(5);
    });

    it('should reset selection to (0,0)', () => {
      service.selectOnClick(2, 2);
      const grid = createEmptyGrid('g2', '', 5, 5);
      service.initGrid(grid);
      expect(service.selectedX()).toBe(0);
      expect(service.selectedY()).toBe(0);
    });

    it('should reset direction to NONE', () => {
      service.initGrid(createEmptyGrid('g3', '', 5, 5));
      expect(service.direction()).toBe('NONE');
    });
  });

  describe('selectOnClick', () => {
    beforeEach(() => {
      service.initGrid(createEmptyGrid('g', '', 5, 5));
    });

    it('should update selectedX and selectedY', () => {
      service.selectOnClick(3, 2);
      expect(service.selectedX()).toBe(3);
      expect(service.selectedY()).toBe(2);
    });

    it('should not update for out-of-bounds coordinates', () => {
      service.selectOnClick(3, 2);
      service.selectOnClick(10, 10);
      expect(service.selectedX()).toBe(3);
      expect(service.selectedY()).toBe(2);
    });

    it('should inverse direction when clicking the same cell twice', () => {
      // Set a direction first via a clue neighbor
      const grid = createEmptyGrid('g', '', 5, 5);
      const clueCell = {
        x: 0, y: 0, type: 'CLUE_SINGLE' as const,
        clues: [{ direction: 'RIGHT' as const, text: 'test' }],
      };
      const gridWithClue = withUpdatedCell(grid, clueCell);
      service.initGrid(gridWithClue);

      // Click cell (1,0) which is to the right of the clue
      service.selectOnClick(1, 0);
      expect(service.direction()).toBe('RIGHTWARDS');

      // Click again to inverse direction
      service.selectOnClick(1, 0);
      expect(service.direction()).toBe('DOWNWARDS');
    });
  });

  describe('move', () => {
    beforeEach(() => {
      service.initGrid(createEmptyGrid('g', '', 5, 5));
      service.selectOnClick(2, 2);
    });

    it('moveUp should decrease Y', () => {
      service.moveUp();
      expect(service.selectedY()).toBe(1);
    });

    it('moveDown should increase Y', () => {
      service.moveDown();
      expect(service.selectedY()).toBe(3);
    });

    it('moveLeft should decrease X', () => {
      service.moveLeft();
      expect(service.selectedX()).toBe(1);
    });

    it('moveRight should increase X', () => {
      service.moveRight();
      expect(service.selectedX()).toBe(3);
    });

    it('should not move out of bounds', () => {
      service.selectOnClick(0, 0);
      service.moveUp();
      expect(service.selectedY()).toBe(0);
      service.moveLeft();
      expect(service.selectedX()).toBe(0);
    });

    it('should not exceed grid dimensions', () => {
      service.selectOnClick(4, 4);
      service.moveDown();
      expect(service.selectedY()).toBe(4);
      service.moveRight();
      expect(service.selectedX()).toBe(4);
    });
  });

  describe('inputLetter', () => {
    beforeEach(() => {
      const grid = createEmptyGrid('g', '', 5, 5);
      const clueCell = {
        x: 0, y: 0, type: 'CLUE_SINGLE' as const,
        clues: [{ direction: 'RIGHT' as const, text: 'test' }],
      };
      const gridWithClue = withUpdatedCell(grid, clueCell);
      service.initGrid(gridWithClue);
    });

    it('should write an uppercase letter in the selected cell', () => {
      service.selectOnClick(1, 0);
      service.inputLetter('a');
      const cell = getCell(service.grid(), 1, 0);
      expect(cell!.letter!.value).toBe('A');
    });

    it('should advance to next LETTER cell in direction', () => {
      service.selectOnClick(1, 0);
      // Direction should be RIGHTWARDS from the clue
      expect(service.direction()).toBe('RIGHTWARDS');
      service.inputLetter('a');
      expect(service.selectedX()).toBe(2);
    });

    it('should not write letter in a non-LETTER cell', () => {
      service.selectOnClick(0, 0); // This is the clue cell
      service.inputLetter('a');
      const cell = getCell(service.grid(), 0, 0);
      expect(cell!.type).toBe('CLUE_SINGLE');
    });
  });

  describe('handleBackspace', () => {
    beforeEach(() => {
      const grid = createEmptyGrid('g', '', 5, 5);
      const clueCell = {
        x: 0, y: 0, type: 'CLUE_SINGLE' as const,
        clues: [{ direction: 'RIGHT' as const, text: 'test' }],
      };
      const gridWithClue = withUpdatedCell(grid, clueCell);
      service.initGrid(gridWithClue);
    });

    it('should erase the current letter and move backwards', () => {
      service.selectOnClick(2, 0);
      service.inputLetter('b');
      // Now at (3, 0), move back to (2, 0) via backspace
      service.handleBackspace();
      // Should have erased (3, 0) and moved to (2, 0)
      const cell = getCell(service.grid(), 3, 0);
      expect(cell!.letter!.value).toBe('');
      expect(service.selectedX()).toBe(2);
    });
  });

  describe('onCellTypeChange', () => {
    beforeEach(() => {
      service.initGrid(createEmptyGrid('g', '', 5, 5));
    });

    it('should change cell to BLACK', () => {
      service.selectOnClick(1, 1);
      service.onCellTypeChange('BLACK');
      const cell = getCell(service.grid(), 1, 1);
      expect(cell!.type).toBe('BLACK');
    });

    it('should change cell to CLUE_SINGLE', () => {
      service.selectOnClick(1, 1);
      service.onCellTypeChange('CLUE_SINGLE');
      const cell = getCell(service.grid(), 1, 1);
      expect(cell!.type).toBe('CLUE_SINGLE');
      expect(cell!.clues).toHaveLength(1);
    });

    it('should change cell to CLUE_DOUBLE', () => {
      service.selectOnClick(1, 1);
      service.onCellTypeChange('CLUE_DOUBLE');
      const cell = getCell(service.grid(), 1, 1);
      expect(cell!.type).toBe('CLUE_DOUBLE');
      expect(cell!.clues).toHaveLength(2);
    });

    it('should change cell back to LETTER', () => {
      service.selectOnClick(1, 1);
      service.onCellTypeChange('BLACK');
      service.onCellTypeChange('LETTER');
      const cell = getCell(service.grid(), 1, 1);
      expect(cell!.type).toBe('LETTER');
    });
  });

  describe('onResize', () => {
    beforeEach(() => {
      service.initGrid(createEmptyGrid('g', '', 5, 5));
    });

    it('should resize the grid', () => {
      service.onResize(8, 8);
      expect(service.grid().width).toBe(8);
      expect(service.grid().height).toBe(8);
    });

    it('should clamp selection when grid shrinks', () => {
      service.selectOnClick(4, 4);
      service.onResize(3, 3);
      expect(service.selectedX()).toBe(2);
      expect(service.selectedY()).toBe(2);
    });

    it('should not resize if size is invalid', () => {
      service.onResize(1, 1); // Below minimum
      expect(service.grid().width).toBe(5);
      expect(service.grid().height).toBe(5);
    });
  });

  describe('selectedCell', () => {
    it('should return the cell at the current selection', () => {
      service.initGrid(createEmptyGrid('g', '', 5, 5));
      service.selectOnClick(2, 3);
      const cell = service.selectedCell();
      expect(cell).not.toBeNull();
      expect(cell!.x).toBe(2);
      expect(cell!.y).toBe(3);
    });
  });

  describe('setLetterAt / clearLetterAt', () => {
    beforeEach(() => {
      service.initGrid(createEmptyGrid('g', '', 5, 5));
    });

    it('setLetterAt should write the letter without moving cursor', () => {
      service.setLetterAt(2, 2, 'Z');
      const cell = getCell(service.grid(), 2, 2);
      expect(cell!.letter!.value).toBe('Z');
      expect(service.selectedX()).toBe(0);
      expect(service.selectedY()).toBe(0);
    });

    it('setLetterAt should overwrite an existing letter', () => {
      service.setLetterAt(1, 1, 'A');
      service.setLetterAt(1, 1, 'B');
      expect(getCell(service.grid(), 1, 1)!.letter!.value).toBe('B');
    });

    it('clearLetterAt should erase the letter', () => {
      service.setLetterAt(3, 0, 'X');
      service.clearLetterAt(3, 0);
      expect(getCell(service.grid(), 3, 0)!.letter!.value).toBe('');
    });

    it('setLetterAt on a non-LETTER cell should do nothing', () => {
      const grid = createEmptyGrid('g', '', 5, 5);
      const clue = { x: 0, y: 0, type: 'CLUE_SINGLE' as const, clues: [{ direction: 'RIGHT' as const, text: 'x' }] };
      service.initGrid(withUpdatedCell(grid, clue));
      service.setLetterAt(0, 0, 'A');
      const cell = getCell(service.grid(), 0, 0);
      expect(cell!.type).toBe('CLUE_SINGLE');
    });

    it('setLetterAt out of bounds should not throw', () => {
      expect(() => service.setLetterAt(99, 99, 'A')).not.toThrow();
    });
  });

  describe('rows', () => {
    it('should return cells grouped by rows', () => {
      service.initGrid(createEmptyGrid('g', '', 3, 2));
      const rows = service.rows();
      expect(rows).toHaveLength(2);
      expect(rows[0]).toHaveLength(3);
      expect(rows[1]).toHaveLength(3);
      expect(rows[0][0].x).toBe(0);
      expect(rows[0][0].y).toBe(0);
      expect(rows[1][2].x).toBe(2);
      expect(rows[1][2].y).toBe(1);
    });
  });
});
