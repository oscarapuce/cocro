import {Cell, Clue, Grid, Letter} from '@domain/models/grid.model';
import {isCellClue, isCellLetter} from '@domain/services/cell-utils.service';

function isLetterValid(letter: Letter | undefined): boolean {
  if (!letter) return false;
  return !!letter.value && /^[A-Z]$/.test(letter.value);
}

function isClueValid(clue: Clue): boolean {
  return clue.text.trim().length > 0;
}

function isCellValid(cell: Cell): boolean {
  if (isCellLetter(cell)) {
    return isLetterValid(cell.letter); // si pas de clue, lettre obligatoire
  } else if (isCellClue(cell)) {
    return cell.clues!.every(isClueValid); // sinon on valide juste les clues
  }
  return true; // pas de validité pour les BLACK
}

export function isGridFullyFilled(grid: Grid): boolean {
  return grid.cells.every(isCellValid);
}

export function isLetterFullyFilled(grid: Grid): boolean {
  return grid.cells.every(cell => {
    if (cell.clues && cell.clues.length > 0) {
      return true; // si la cellule a des clues, on ne vérifie pas la lettre
    }
    return isLetterValid(cell.letter);
  });
}
