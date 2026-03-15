import { Cell, CellType, ClueDirection, GridDifficulty } from '@domain/models/grid.model';

export interface ClueDto {
  direction: ClueDirection;
  text: string;
}

export interface CellDto {
  x: number;
  y: number;
  type: CellType;
  letter?: string;
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
    number: cell.letter?.number,
    clues: cell.clues?.length ? cell.clues : undefined,
  };
}
