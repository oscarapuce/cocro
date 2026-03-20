import { Cell, Grid } from '@domain/models/grid.model';
import {
  getCell,
  withUpdatedCell,
  createEmptyGrid,
  resizeGrid,
  isOutOfBounds,
  isValidSize,
  getDirectionFromSurroundingClue,
  MIN_GRID_WIDTH,
  MIN_GRID_HEIGHT,
  MAX_GRID_WIDTH,
  MAX_GRID_HEIGHT,
} from './grid-utils.service';

describe('grid-utils', () => {
  let grid: Grid;

  beforeEach(() => {
    grid = createEmptyGrid('test-id', 'Test', 3, 3);
  });

  describe('createEmptyGrid', () => {
    it('should create a grid with correct dimensions', () => {
      expect(grid.id).toBe('test-id');
      expect(grid.title).toBe('Test');
      expect(grid.width).toBe(3);
      expect(grid.height).toBe(3);
      expect(grid.cells).toHaveLength(9);
    });

    it('should assign correct coordinates to each cell', () => {
      expect(grid.cells[0]).toEqual(expect.objectContaining({ x: 0, y: 0 }));
      expect(grid.cells[1]).toEqual(expect.objectContaining({ x: 1, y: 0 }));
      expect(grid.cells[3]).toEqual(expect.objectContaining({ x: 0, y: 1 }));
      expect(grid.cells[8]).toEqual(expect.objectContaining({ x: 2, y: 2 }));
    });

    it('should create all cells as LETTER type with empty value', () => {
      for (const cell of grid.cells) {
        expect(cell.type).toBe('LETTER');
        expect(cell.letter!.value).toBe('');
        expect(cell.letter!.separator).toBe('NONE');
      }
    });
  });

  describe('getCell', () => {
    it('should return the cell at given coordinates', () => {
      const cell = getCell(grid, 1, 2);
      expect(cell).not.toBeNull();
      expect(cell!.x).toBe(1);
      expect(cell!.y).toBe(2);
    });

    it('should return null for negative coordinates', () => {
      expect(getCell(grid, -1, 0)).toBeNull();
      expect(getCell(grid, 0, -1)).toBeNull();
    });

    it('should return null for out-of-bounds coordinates', () => {
      expect(getCell(grid, 3, 0)).toBeNull();
      expect(getCell(grid, 0, 3)).toBeNull();
    });
  });

  describe('withUpdatedCell', () => {
    it('should return a new grid with the cell updated', () => {
      const updatedCell: Cell = { x: 1, y: 1, type: 'BLACK' };
      const newGrid = withUpdatedCell(grid, updatedCell);

      expect(newGrid).not.toBe(grid);
      expect(getCell(newGrid, 1, 1)!.type).toBe('BLACK');
      // original unchanged
      expect(getCell(grid, 1, 1)!.type).toBe('LETTER');
    });

    it('should throw RangeError for out-of-bounds cell', () => {
      const outOfBounds: Cell = { x: 10, y: 10, type: 'LETTER' };
      expect(() => withUpdatedCell(grid, outOfBounds)).toThrow(RangeError);
    });

    it('should throw RangeError for negative coordinates', () => {
      const negative: Cell = { x: -1, y: 0, type: 'LETTER' };
      expect(() => withUpdatedCell(grid, negative)).toThrow(RangeError);
    });
  });

  describe('resizeGrid', () => {
    it('should grow the grid and fill new cells with LETTER', () => {
      const bigger = resizeGrid(grid, 5, 5);
      expect(bigger.width).toBe(5);
      expect(bigger.height).toBe(5);
      expect(bigger.cells).toHaveLength(25);
      // new cell at (4, 4) should be a default LETTER
      const newCell = getCell(bigger, 4, 4);
      expect(newCell!.type).toBe('LETTER');
      expect(newCell!.letter!.value).toBe('');
    });

    it('should shrink the grid and preserve existing cells', () => {
      const smaller = resizeGrid(grid, 2, 2);
      expect(smaller.width).toBe(2);
      expect(smaller.height).toBe(2);
      expect(smaller.cells).toHaveLength(4);
    });

    it('should preserve content of cells within the new bounds', () => {
      const modified: Cell = {
        x: 0, y: 0, type: 'LETTER',
        letter: { value: 'X', separator: 'NONE' },
      };
      const modifiedGrid = withUpdatedCell(grid, modified);
      const resized = resizeGrid(modifiedGrid, 5, 5);
      expect(getCell(resized, 0, 0)!.letter!.value).toBe('X');
    });
  });

  describe('isOutOfBounds', () => {
    it('should return true for negative coordinates', () => {
      expect(isOutOfBounds(-1, 0, 10, 10)).toBe(true);
      expect(isOutOfBounds(0, -1, 10, 10)).toBe(true);
    });

    it('should return true for coordinates equal to width/height', () => {
      expect(isOutOfBounds(10, 0, 10, 10)).toBe(true);
      expect(isOutOfBounds(0, 10, 10, 10)).toBe(true);
    });

    it('should return false for valid coordinates', () => {
      expect(isOutOfBounds(0, 0, 10, 10)).toBe(false);
      expect(isOutOfBounds(9, 9, 10, 10)).toBe(false);
    });
  });

  describe('isValidSize', () => {
    it('should return true for sizes within bounds', () => {
      expect(isValidSize(MIN_GRID_WIDTH, MIN_GRID_HEIGHT)).toBe(true);
      expect(isValidSize(MAX_GRID_WIDTH, MAX_GRID_HEIGHT)).toBe(true);
      expect(isValidSize(10, 10)).toBe(true);
    });

    it('should return false for sizes below minimum', () => {
      expect(isValidSize(MIN_GRID_WIDTH - 1, MIN_GRID_HEIGHT)).toBe(false);
      expect(isValidSize(MIN_GRID_WIDTH, MIN_GRID_HEIGHT - 1)).toBe(false);
    });

    it('should return false for sizes above maximum', () => {
      expect(isValidSize(MAX_GRID_WIDTH + 1, MAX_GRID_HEIGHT)).toBe(false);
      expect(isValidSize(MAX_GRID_WIDTH, MAX_GRID_HEIGHT + 1)).toBe(false);
    });
  });

  describe('getDirectionFromSurroundingClue', () => {
    it('should return NONE when cell itself has clues', () => {
      const clueCell: Cell = {
        x: 0, y: 0, type: 'CLUE_SINGLE',
        clues: [{ direction: 'RIGHT', text: 'test' }],
      };
      expect(getDirectionFromSurroundingClue(clueCell, grid)).toBe('NONE');
    });

    it('should return RIGHTWARDS when left neighbor has a RIGHT clue', () => {
      const clueCell: Cell = {
        x: 0, y: 0, type: 'CLUE_SINGLE',
        clues: [{ direction: 'RIGHT', text: 'test' }],
      };
      const g = withUpdatedCell(grid, clueCell);
      const letterCell = getCell(g, 1, 0)!;
      expect(getDirectionFromSurroundingClue(letterCell, g)).toBe('RIGHTWARDS');
    });

    it('should return DOWNWARDS when top neighbor has a DOWN clue', () => {
      const clueCell: Cell = {
        x: 0, y: 0, type: 'CLUE_SINGLE',
        clues: [{ direction: 'DOWN', text: 'test' }],
      };
      const g = withUpdatedCell(grid, clueCell);
      const letterCell = getCell(g, 0, 1)!;
      expect(getDirectionFromSurroundingClue(letterCell, g)).toBe('DOWNWARDS');
    });

    it('should return NONE when no adjacent clue cells', () => {
      const cell = getCell(grid, 1, 1)!;
      expect(getDirectionFromSurroundingClue(cell, grid)).toBe('NONE');
    });
  });
});
