export interface GridTemplateResponse {
  title: string;
  width: number;
  height: number;
  difficulty: string;
  author: string;
  reference?: string;
  description?: string;
  globalClueLabel?: string;
  globalClueWordLengths?: number[];
  cells: GridTemplateCellDto[];
}

export interface GridTemplateCellDto {
  x: number;
  y: number;
  type: 'LETTER' | 'CLUE_SINGLE' | 'CLUE_DOUBLE' | 'BLACK';
  separator?: 'LEFT' | 'UP' | 'BOTH' | 'NONE' | null;
  number?: number | null;
  clues?: GridTemplateClueDto[] | null;
}

export interface GridTemplateClueDto {
  direction: 'RIGHT' | 'DOWN' | 'FROM_BELOW' | 'FROM_SIDE';
  text: string;
}
