import { Cell, CellType, ClueDirection, Grid, GridDifficulty, SeparatorType } from '@domain/models/grid.model';
import { GridTemplateCellDto, GridTemplateResponse } from '@domain/models/grid-template.model';

export function mapGridTemplateToGrid(dto: GridTemplateResponse): Grid {
  const cells: Cell[] = dto.cells.map(c => mapCell(c));
  return {
    id: '',
    title: dto.title,
    reference: dto.reference,
    width: dto.width,
    height: dto.height,
    cells,
    author: dto.author,
    difficulty: (dto.difficulty ?? 'NONE') as GridDifficulty,
    description: dto.description,
    globalClue: dto.globalClueLabel
      ? { label: dto.globalClueLabel, wordLengths: dto.globalClueWordLengths ?? [] }
      : undefined,
  };
}

function mapCell(c: GridTemplateCellDto): Cell {
  const base = { x: c.x, y: c.y, type: c.type as CellType };
  switch (c.type) {
    case 'LETTER':
      return {
        ...base,
        letter: {
          value: '',  // lettre vide = template
          separator: (c.separator ?? 'NONE') as SeparatorType,
          number: c.number ?? undefined,
        },
      };
    case 'CLUE_SINGLE':
    case 'CLUE_DOUBLE':
      return {
        ...base,
        clues: (c.clues ?? []).map(cl => ({
          direction: cl.direction as ClueDirection,
          text: cl.text,
        })),
      };
    case 'BLACK':
    default:
      return base;
  }
}
