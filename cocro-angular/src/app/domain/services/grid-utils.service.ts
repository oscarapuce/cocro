import { Cell, ClueDirection, Direction, Grid } from '@domain/models/grid.model';
import { DEFAULT_LETTER } from '@domain/services/cell-utils.service';

export const MIN_GRID_WIDTH = 3;
export const MIN_GRID_HEIGHT = 3;
export const MAX_GRID_WIDTH = 50;
export const MAX_GRID_HEIGHT = 25;

export function getCell(grid: Grid, x: number, y: number): Cell | null {
  if (x < 0 || y < 0 || x >= grid.width || y >= grid.height
    || !grid.cells || (y * grid.width + x >= grid.cells.length)) {
    return null;
  }
  return grid.cells[y * grid.width + x];
}

export function withUpdatedCell(grid: Grid, cell: Cell): Grid {
  if (cell.x < 0 || cell.y < 0 || cell.x >= grid.width || cell.y >= grid.height) {
    throw new RangeError('Cell coordinates out of bounds');
  }

  const index = cell.y * grid.width + cell.x;
  const newCells = [...grid.cells];
  newCells[index] = cell;

  return { ...grid, cells: newCells };
}

export function createEmptyGrid(id: string, title: string, width: number, height: number): Grid {
  return {
    id,
    title,
    width,
    height,
    cells: Array.from({ length: width * height }, (_, i) => ({
      x: i % width,
      y: Math.floor(i / width),
      type: 'LETTER' as const,
      letter: { value: '', separator: 'NONE' as const },
    })),
  };
}

export function resizeGrid(grid: Grid, newWidth: number, newHeight: number): Grid {
  const newCells: Cell[] = [];

  for (let y = 0; y < newHeight; y++) {
    for (let x = 0; x < newWidth; x++) {
      const newIndex = y * newWidth + x;

      if (x < grid.width && y < grid.height) {
        const oldCell = grid.cells[y * grid.width + x];
        newCells[newIndex] = { ...oldCell, x, y };
      } else {
        newCells[newIndex] = { x, y, type: 'LETTER', letter: { ...DEFAULT_LETTER } };
      }
    }
  }

  return { ...grid, width: newWidth, height: newHeight, cells: newCells };
}

export function isOutOfBounds(x: number, y: number, width: number, height: number): boolean {
  return x < 0 || x >= width || y < 0 || y >= height;
}

export function isValidSize(width: number, height: number): boolean {
  return (
    width >= MIN_GRID_WIDTH && width <= MAX_GRID_WIDTH &&
    height >= MIN_GRID_HEIGHT && height <= MAX_GRID_HEIGHT
  );
}

export function getDirectionFromSurroundingClue(cell: Cell, grid: Grid): Direction {
  if (cell.clues?.length) return 'NONE';

  const directions: { dx: number; dy: number; clueDir: ClueDirection[] }[] = [
    { dx: -1, dy: 0, clueDir: ['RIGHT', 'FROM_SIDE'] },
    { dx: 0, dy: -1, clueDir: ['DOWN', 'FROM_BELOW'] },
  ];

  for (const { dx, dy, clueDir } of directions) {
    const nx = cell.x + dx;
    const ny = cell.y + dy;

    if (nx < 0 || ny < 0 || nx >= grid.width || ny >= grid.height) continue;

    const adjacent = grid.cells[ny * grid.width + nx];
    if (!adjacent.clues?.length) continue;

    for (const clue of adjacent.clues) {
      if (clueDir.includes(clue.direction)) {
        if (clue.direction === 'DOWN' || clue.direction === 'FROM_SIDE') return 'DOWNWARDS';
        if (clue.direction === 'RIGHT' || clue.direction === 'FROM_BELOW') return 'RIGHTWARDS';
      }
    }
  }

  return 'NONE';
}
