import { mapGridTemplateToGrid } from './grid-template.mapper';
import { GridTemplateResponse } from '@domain/models/grid-template.model';

const BASE_DTO: GridTemplateResponse = {
  title: 'Test Grid',
  width: 3,
  height: 2,
  difficulty: '2',
  author: 'Author',
  reference: 'REF-01',
  description: 'A test grid',
  globalClueLabel: 'Mystery word',
  globalClueWordLengths: [3, 4],
  cells: [],
};

describe('mapGridTemplateToGrid', () => {
  it('should map top-level metadata', () => {
    const grid = mapGridTemplateToGrid({ ...BASE_DTO, cells: [] });
    expect(grid.title).toBe('Test Grid');
    expect(grid.width).toBe(3);
    expect(grid.height).toBe(2);
    expect(grid.difficulty).toBe('2');
    expect(grid.author).toBe('Author');
    expect(grid.reference).toBe('REF-01');
    expect(grid.description).toBe('A test grid');
  });

  it('should map globalClue when present', () => {
    const grid = mapGridTemplateToGrid({ ...BASE_DTO, cells: [] });
    expect(grid.globalClue).toEqual({ label: 'Mystery word', wordLengths: [3, 4] });
  });

  it('should set globalClue to undefined when absent', () => {
    const grid = mapGridTemplateToGrid({ ...BASE_DTO, globalClueLabel: undefined, globalClueWordLengths: undefined, cells: [] });
    expect(grid.globalClue).toBeUndefined();
  });

  it('should default globalClueWordLengths to [] when label present but lengths absent', () => {
    const grid = mapGridTemplateToGrid({ ...BASE_DTO, globalClueLabel: 'Word', globalClueWordLengths: undefined, cells: [] });
    expect(grid.globalClue).toEqual({ label: 'Word', wordLengths: [] });
  });

  it('should default difficulty to NONE when null/undefined', () => {
    const grid = mapGridTemplateToGrid({ ...BASE_DTO, difficulty: undefined as any, cells: [] });
    expect(grid.difficulty).toBe('NONE');
  });

  it('should set id to empty string', () => {
    const grid = mapGridTemplateToGrid({ ...BASE_DTO, cells: [] });
    expect(grid.id).toBe('');
  });

  describe('LETTER cell', () => {
    it('should map to a letter cell with empty value', () => {
      const dto: GridTemplateResponse = {
        ...BASE_DTO,
        cells: [{ x: 1, y: 0, type: 'LETTER', separator: 'LEFT', number: 3, clues: null }],
      };
      const grid = mapGridTemplateToGrid(dto);
      const cell = grid.cells[0];
      expect(cell.type).toBe('LETTER');
      expect(cell.x).toBe(1);
      expect(cell.y).toBe(0);
      expect(cell.letter!.value).toBe('');
      expect(cell.letter!.separator).toBe('LEFT');
      expect(cell.letter!.number).toBe(3);
    });

    it('should default separator to NONE when absent', () => {
      const dto: GridTemplateResponse = {
        ...BASE_DTO,
        cells: [{ x: 0, y: 0, type: 'LETTER', separator: null, number: null, clues: null }],
      };
      const grid = mapGridTemplateToGrid(dto);
      expect(grid.cells[0].letter!.separator).toBe('NONE');
    });

    it('should set number to undefined when absent', () => {
      const dto: GridTemplateResponse = {
        ...BASE_DTO,
        cells: [{ x: 0, y: 0, type: 'LETTER', separator: 'NONE', number: null, clues: null }],
      };
      const grid = mapGridTemplateToGrid(dto);
      expect(grid.cells[0].letter!.number).toBeUndefined();
    });
  });

  describe('CLUE_SINGLE cell', () => {
    it('should map clues', () => {
      const dto: GridTemplateResponse = {
        ...BASE_DTO,
        cells: [{ x: 0, y: 0, type: 'CLUE_SINGLE', separator: null, number: null, clues: [{ direction: 'RIGHT', text: 'A clue' }] }],
      };
      const grid = mapGridTemplateToGrid(dto);
      const cell = grid.cells[0];
      expect(cell.type).toBe('CLUE_SINGLE');
      expect(cell.clues).toHaveLength(1);
      expect(cell.clues![0].direction).toBe('RIGHT');
      expect(cell.clues![0].text).toBe('A clue');
    });

    it('should default to empty clues when null', () => {
      const dto: GridTemplateResponse = {
        ...BASE_DTO,
        cells: [{ x: 0, y: 0, type: 'CLUE_SINGLE', separator: null, number: null, clues: null }],
      };
      const grid = mapGridTemplateToGrid(dto);
      expect(grid.cells[0].clues).toEqual([]);
    });
  });

  describe('CLUE_DOUBLE cell', () => {
    it('should map both clues', () => {
      const dto: GridTemplateResponse = {
        ...BASE_DTO,
        cells: [{
          x: 0, y: 1, type: 'CLUE_DOUBLE', separator: null, number: null,
          clues: [
            { direction: 'RIGHT', text: 'First' },
            { direction: 'DOWN', text: 'Second' },
          ],
        }],
      };
      const grid = mapGridTemplateToGrid(dto);
      const cell = grid.cells[0];
      expect(cell.type).toBe('CLUE_DOUBLE');
      expect(cell.clues).toHaveLength(2);
      expect(cell.clues![1].direction).toBe('DOWN');
    });
  });

  describe('BLACK cell', () => {
    it('should map to a black cell with no letter/clues', () => {
      const dto: GridTemplateResponse = {
        ...BASE_DTO,
        cells: [{ x: 2, y: 1, type: 'BLACK', separator: null, number: null, clues: null }],
      };
      const grid = mapGridTemplateToGrid(dto);
      const cell = grid.cells[0];
      expect(cell.type).toBe('BLACK');
      expect(cell.x).toBe(2);
      expect(cell.y).toBe(1);
      expect((cell as any).letter).toBeUndefined();
      expect((cell as any).clues).toBeUndefined();
    });
  });

  it('should map multiple cells preserving order', () => {
    const dto: GridTemplateResponse = {
      ...BASE_DTO,
      cells: [
        { x: 0, y: 0, type: 'CLUE_SINGLE', separator: null, number: null, clues: [{ direction: 'RIGHT', text: 'X' }] },
        { x: 1, y: 0, type: 'LETTER', separator: 'NONE', number: 1, clues: null },
        { x: 2, y: 0, type: 'BLACK', separator: null, number: null, clues: null },
      ],
    };
    const grid = mapGridTemplateToGrid(dto);
    expect(grid.cells).toHaveLength(3);
    expect(grid.cells[0].type).toBe('CLUE_SINGLE');
    expect(grid.cells[1].type).toBe('LETTER');
    expect(grid.cells[2].type).toBe('BLACK');
  });
});
