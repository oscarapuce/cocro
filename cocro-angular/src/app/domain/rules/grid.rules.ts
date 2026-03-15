import { Cell, Clue, Grid, Letter } from '@domain/models/grid.model';
import { isCellClue, isCellLetter } from '@domain/services/cell-utils';

export function isLetterValid(letter: Letter | undefined): boolean {
  if (!letter) return false;
  return !!letter.value && /^[A-Z]$/.test(letter.value);
}

export function isClueValid(clue: Clue): boolean {
  return clue.text.trim().length > 0;
}

export function isCellValid(cell: Cell): boolean {
  if (isCellLetter(cell)) return isLetterValid(cell.letter);
  if (isCellClue(cell)) return cell.clues!.every(isClueValid);
  return true;
}

export function isGridFullyFilled(grid: Grid): boolean {
  return grid.cells.every(isCellValid);
}

export function isLetterFullyFilled(grid: Grid): boolean {
  return grid.cells.every(cell => {
    if (cell.clues && cell.clues.length > 0) return true;
    return isLetterValid(cell.letter);
  });
}
