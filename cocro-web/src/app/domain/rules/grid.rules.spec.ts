import { Cell, Grid } from '@domain/models/grid.model';
import { isGridFullyFilled, isLetterFullyFilled } from './grid.rules';

function makeGrid(cells: Cell[]): Grid {
  return { id: '1', title: 'Test', width: 3, height: 1, cells };
}

describe('grid.rules', () => {
  describe('isGridFullyFilled', () => {
    it('should return true when all LETTER cells have valid letters and all CLUE cells have text', () => {
      const grid = makeGrid([
        { x: 0, y: 0, type: 'CLUE_SINGLE', clues: [{ direction: 'RIGHT', text: 'A clue' }] },
        { x: 1, y: 0, type: 'LETTER', letter: { value: 'A', separator: 'NONE' } },
        { x: 2, y: 0, type: 'LETTER', letter: { value: 'B', separator: 'NONE' } },
      ]);
      expect(isGridFullyFilled(grid)).toBe(true);
    });

    it('should return false when a LETTER cell is empty', () => {
      const grid = makeGrid([
        { x: 0, y: 0, type: 'LETTER', letter: { value: '', separator: 'NONE' } },
        { x: 1, y: 0, type: 'LETTER', letter: { value: 'A', separator: 'NONE' } },
        { x: 2, y: 0, type: 'LETTER', letter: { value: 'B', separator: 'NONE' } },
      ]);
      expect(isGridFullyFilled(grid)).toBe(false);
    });

    it('should return false when a CLUE cell has empty text', () => {
      const grid = makeGrid([
        { x: 0, y: 0, type: 'CLUE_SINGLE', clues: [{ direction: 'RIGHT', text: '' }] },
        { x: 1, y: 0, type: 'LETTER', letter: { value: 'A', separator: 'NONE' } },
        { x: 2, y: 0, type: 'LETTER', letter: { value: 'B', separator: 'NONE' } },
      ]);
      expect(isGridFullyFilled(grid)).toBe(false);
    });

    it('should return true when all cells are BLACK', () => {
      const grid = makeGrid([
        { x: 0, y: 0, type: 'BLACK' },
        { x: 1, y: 0, type: 'BLACK' },
        { x: 2, y: 0, type: 'BLACK' },
      ]);
      expect(isGridFullyFilled(grid)).toBe(true);
    });

    it('should return false for LETTER cell with lowercase value', () => {
      const grid = makeGrid([
        { x: 0, y: 0, type: 'LETTER', letter: { value: 'a', separator: 'NONE' } },
      ]);
      // Only uppercase A-Z passes the regex
      expect(isGridFullyFilled(grid)).toBe(false);
    });

    it('should return false when LETTER cell has no letter', () => {
      const grid = makeGrid([
        { x: 0, y: 0, type: 'LETTER' },
      ]);
      expect(isGridFullyFilled(grid)).toBe(false);
    });

    it('should validate all clues in a CLUE_DOUBLE cell', () => {
      const gridValid = makeGrid([
        {
          x: 0, y: 0, type: 'CLUE_DOUBLE',
          clues: [
            { direction: 'RIGHT', text: 'Clue 1' },
            { direction: 'DOWN', text: 'Clue 2' },
          ],
        },
        { x: 1, y: 0, type: 'LETTER', letter: { value: 'A', separator: 'NONE' } },
        { x: 2, y: 0, type: 'LETTER', letter: { value: 'B', separator: 'NONE' } },
      ]);
      expect(isGridFullyFilled(gridValid)).toBe(true);

      const gridInvalid = makeGrid([
        {
          x: 0, y: 0, type: 'CLUE_DOUBLE',
          clues: [
            { direction: 'RIGHT', text: 'Clue 1' },
            { direction: 'DOWN', text: '' },
          ],
        },
        { x: 1, y: 0, type: 'LETTER', letter: { value: 'A', separator: 'NONE' } },
        { x: 2, y: 0, type: 'LETTER', letter: { value: 'B', separator: 'NONE' } },
      ]);
      expect(isGridFullyFilled(gridInvalid)).toBe(false);
    });
  });

  describe('isLetterFullyFilled', () => {
    it('should return true when all letter cells have valid letters', () => {
      const grid = makeGrid([
        { x: 0, y: 0, type: 'LETTER', letter: { value: 'A', separator: 'NONE' } },
        { x: 1, y: 0, type: 'LETTER', letter: { value: 'B', separator: 'NONE' } },
        { x: 2, y: 0, type: 'LETTER', letter: { value: 'C', separator: 'NONE' } },
      ]);
      expect(isLetterFullyFilled(grid)).toBe(true);
    });

    it('should skip cells with clues', () => {
      const grid = makeGrid([
        { x: 0, y: 0, type: 'CLUE_SINGLE', clues: [{ direction: 'RIGHT', text: '' }] },
        { x: 1, y: 0, type: 'LETTER', letter: { value: 'A', separator: 'NONE' } },
        { x: 2, y: 0, type: 'LETTER', letter: { value: 'B', separator: 'NONE' } },
      ]);
      // Clue cells are skipped even if their text is empty
      expect(isLetterFullyFilled(grid)).toBe(true);
    });

    it('should return false when a letter cell is empty', () => {
      const grid = makeGrid([
        { x: 0, y: 0, type: 'LETTER', letter: { value: '', separator: 'NONE' } },
        { x: 1, y: 0, type: 'LETTER', letter: { value: 'A', separator: 'NONE' } },
        { x: 2, y: 0, type: 'LETTER', letter: { value: 'B', separator: 'NONE' } },
      ]);
      expect(isLetterFullyFilled(grid)).toBe(false);
    });

    it('should return false for BLACK cells without letter', () => {
      // BLACK cells have no clues and no valid letter, so they fail the check
      const grid = makeGrid([
        { x: 0, y: 0, type: 'BLACK' },
      ]);
      expect(isLetterFullyFilled(grid)).toBe(false);
    });
  });
});
