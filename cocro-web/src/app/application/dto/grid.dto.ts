import { Cell, CellType, ClueDirection, GridDifficulty, SeparatorType } from '@domain/models/grid.model';

export interface ClueDto {
  direction: ClueDirection;
  text: string;
}

export interface CellDto {
  x: number;
  y: number;
  type: CellType;
  letter?: string;
  separator?: SeparatorType;
  number?: number;
  clues?: ClueDto[];
}

export interface SubmitGridRequest {
  gridId?: string;
  title: string;
  difficulty: GridDifficulty;
  reference?: string;
  description?: string;
  width: number;
  height: number;
  cells: CellDto[];
  globalClueLabel?: string;
  globalClueWordLengths?: number[];
}

export interface PatchGridRequest {
  gridId: string;
  title?: string;
  difficulty?: GridDifficulty;
  reference?: string;
  description?: string;
  width?: number;
  height?: number;
  cells?: CellDto[];
  globalClueLabel?: string;
  globalClueWordLengths?: number[];
}

export interface GridSubmitResponse {
  gridId: string;
}

export function cellToDto(cell: Cell): CellDto {
  return {
    x: cell.x,
    y: cell.y,
    type: cell.type,
    letter: cell.letter?.value || undefined,
    separator: cell.letter?.separator === 'NONE' ? undefined : cell.letter?.separator,
    number: cell.letter?.number,
    clues: cell.clues?.length ? cell.clues : undefined,
  };
}
