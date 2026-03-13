export type CellType = 'LETTER' | 'CLUE_SINGLE' | 'CLUE_DOUBLE' | 'BLACK';
export type ClueDirection = 'RIGHT' | 'DOWN' | 'FROM_BELOW' | 'FROM_SIDE';
export type GridDifficulty = 'EASY' | 'MEDIUM' | 'HARD' | 'NONE';

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
