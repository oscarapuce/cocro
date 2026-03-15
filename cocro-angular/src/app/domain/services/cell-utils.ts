import { Cell, CellType, Letter, SeparatorType } from '@domain/models/grid.model';

export const DEFAULT_LETTER: Letter = { value: '', separator: 'NONE' };

export function isCellClue(cell: Cell): boolean {
  return cell.type === 'CLUE_SINGLE' || cell.type === 'CLUE_DOUBLE';
}

export function isCellLetter(cell: Cell): boolean {
  return cell.type === 'LETTER';
}

export function isCellClueSingle(cell: Cell): boolean {
  return cell.type === 'CLUE_SINGLE';
}

export function isCellClueDouble(cell: Cell): boolean {
  return cell.type === 'CLUE_DOUBLE';
}

export function isCellBlack(cell: Cell): boolean {
  return cell.type === 'BLACK';
}

export function writeLetterInCell(cell: Cell, letter: string): Cell {
  if (!isCellLetter(cell)) return cell;
  const value = (letter.trim() ?? '').slice(0, 1).toUpperCase();
  return { ...cell, letter: { ...(cell.letter ?? { ...DEFAULT_LETTER }), value } };
}

export function writeNumberInCell(cell: Cell, num: number): Cell {
  if (!isCellLetter(cell)) return cell;
  return { ...cell, letter: { ...(cell.letter ?? { ...DEFAULT_LETTER }), number: num } };
}

export function setLetterInCell(cell: Cell): Cell {
  return { ...cell, type: 'LETTER', letter: { ...DEFAULT_LETTER }, clues: undefined };
}

export function setSingleClueInCell(cell: Cell): Cell {
  return { ...cell, type: 'CLUE_SINGLE', clues: [{ direction: 'RIGHT', text: '' }], letter: undefined };
}

export function setDoubleClueInCell(cell: Cell): Cell {
  return {
    ...cell,
    type: 'CLUE_DOUBLE',
    clues: [
      { direction: 'RIGHT', text: '' },
      { direction: 'DOWN', text: '' },
    ],
    letter: undefined,
  };
}

export function setBlackInCell(cell: Cell): Cell {
  return { ...cell, type: 'BLACK', clues: undefined, letter: undefined };
}

export function setSeparatorInCell(cell: Cell, separator: SeparatorType): Cell {
  if (!isCellLetter(cell)) return cell;
  return { ...cell, letter: { ...(cell.letter ?? { ...DEFAULT_LETTER }), separator } };
}
