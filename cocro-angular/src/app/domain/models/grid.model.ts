export type CellType = 'LETTER' | 'CLUE_SINGLE' | 'CLUE_DOUBLE' | 'BLACK';
export type ClueDirection = 'RIGHT' | 'DOWN' | 'FROM_BELOW' | 'FROM_SIDE';
export type Direction = 'DOWNWARDS' | 'RIGHTWARDS' | 'NONE';
export type SeparatorType = 'LEFT' | 'UP' | 'BOTH' | 'NONE';
export type GridDifficulty = 'EASY' | 'MEDIUM' | 'HARD' | 'NONE';

export interface Clue {
  direction: ClueDirection;
  text: string;
}

export interface Letter {
  value: string;
  separator: SeparatorType;
  number?: number;
}

export interface Cell {
  x: number;
  y: number;
  letter?: Letter;
  clues?: Clue[];
  type: CellType;
}

export interface Grid {
  id: string;
  title: string;
  width: number;
  height: number;
  cells: Cell[];
  author?: string;
  difficulty?: GridDifficulty;
  description?: string;
}
