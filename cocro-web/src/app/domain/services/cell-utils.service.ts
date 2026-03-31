import { Cell, ClueDirection, Letter, SeparatorType, Clue } from '@domain/models/grid.model';

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
  if (cell.type !== 'LETTER') return cell;
  const upper = letter.toUpperCase().slice(0, 1);
  return {
    ...cell,
    letter: { ...(cell.letter ?? DEFAULT_LETTER), value: upper },
  };
}

export function writeNumberInCell(cell: Cell, num: number | undefined): Cell {
  if (cell.type !== 'LETTER') return cell;
  return {
    ...cell,
    letter: { ...(cell.letter ?? DEFAULT_LETTER), number: num },
  };
}

export function setLetterInCell(cell: Cell): Cell {
  return { ...cell, type: 'LETTER', letter: { ...DEFAULT_LETTER }, clues: undefined };
}

export function setSingleClueInCell(cell: Cell): Cell {
  const clue: Clue = { direction: 'RIGHT', text: '' };
  return { ...cell, type: 'CLUE_SINGLE', clues: [clue], letter: undefined };
}

export function setDoubleClueInCell(cell: Cell): Cell {
  const c1: Clue = { direction: 'RIGHT', text: '' };
  const c2: Clue = { direction: 'DOWN', text: '' };
  return { ...cell, type: 'CLUE_DOUBLE', clues: [c1, c2], letter: undefined };
}

export function setBlackInCell(cell: Cell): Cell {
  return { ...cell, type: 'BLACK', letter: undefined, clues: undefined };
}

export function getAllowedClueDirections(index: number, total: number): ClueDirection[] {
  if (total === 1) return ['RIGHT', 'DOWN', 'FROM_SIDE', 'FROM_BELOW'];
  if (total === 2) {
    if (index === 0) return ['RIGHT', 'FROM_SIDE'];
    if (index === 1) return ['DOWN', 'FROM_BELOW'];
  }
  return [];
}

export type SepKey = 'left' | 'up';

export function getSepKeysFromSeparator(sep: SeparatorType): SepKey[] {
  switch (sep) {
    case 'LEFT': return ['left'];
    case 'UP':   return ['up'];
    case 'BOTH': return ['left', 'up'];
    default:     return [];
  }
}

export function toggleSeparatorKey(current: SeparatorType, key: SepKey): SeparatorType {
  const keys = new Set(getSepKeysFromSeparator(current));
  if (keys.has(key)) keys.delete(key); else keys.add(key);
  return keys.has('left') && keys.has('up') ? 'BOTH' :
         keys.has('left') ? 'LEFT' :
         keys.has('up')   ? 'UP' : 'NONE';
}

export function setSeparatorInCell(cell: Cell, separator: SeparatorType): Cell {
  if (cell.type !== 'LETTER') return cell;
  return {
    ...cell,
    letter: { ...(cell.letter ?? DEFAULT_LETTER), separator },
  };
}
