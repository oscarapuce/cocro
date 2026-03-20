import { Cell } from '@domain/models/grid.model';
import {
  DEFAULT_LETTER,
  isCellClue,
  isCellLetter,
  isCellClueSingle,
  isCellClueDouble,
  isCellBlack,
  writeLetterInCell,
  writeNumberInCell,
  setLetterInCell,
  setSingleClueInCell,
  setDoubleClueInCell,
  setBlackInCell,
  setSeparatorInCell,
} from './cell-utils.service';

describe('cell-utils', () => {
  const letterCell: Cell = {
    x: 0, y: 0, type: 'LETTER',
    letter: { value: '', separator: 'NONE' },
  };

  const singleClueCell: Cell = {
    x: 1, y: 0, type: 'CLUE_SINGLE',
    clues: [{ direction: 'RIGHT', text: 'A clue' }],
  };

  const doubleClueCell: Cell = {
    x: 2, y: 0, type: 'CLUE_DOUBLE',
    clues: [
      { direction: 'RIGHT', text: 'Clue 1' },
      { direction: 'DOWN', text: 'Clue 2' },
    ],
  };

  const blackCell: Cell = { x: 3, y: 0, type: 'BLACK' };

  describe('isCellLetter', () => {
    it('should return true for LETTER cells', () => {
      expect(isCellLetter(letterCell)).toBe(true);
    });

    it('should return false for non-LETTER cells', () => {
      expect(isCellLetter(singleClueCell)).toBe(false);
      expect(isCellLetter(blackCell)).toBe(false);
    });
  });

  describe('isCellClue', () => {
    it('should return true for CLUE_SINGLE and CLUE_DOUBLE', () => {
      expect(isCellClue(singleClueCell)).toBe(true);
      expect(isCellClue(doubleClueCell)).toBe(true);
    });

    it('should return false for non-clue cells', () => {
      expect(isCellClue(letterCell)).toBe(false);
      expect(isCellClue(blackCell)).toBe(false);
    });
  });

  describe('isCellClueSingle', () => {
    it('should return true only for CLUE_SINGLE', () => {
      expect(isCellClueSingle(singleClueCell)).toBe(true);
      expect(isCellClueSingle(doubleClueCell)).toBe(false);
    });
  });

  describe('isCellClueDouble', () => {
    it('should return true only for CLUE_DOUBLE', () => {
      expect(isCellClueDouble(doubleClueCell)).toBe(true);
      expect(isCellClueDouble(singleClueCell)).toBe(false);
    });
  });

  describe('isCellBlack', () => {
    it('should return true only for BLACK cells', () => {
      expect(isCellBlack(blackCell)).toBe(true);
      expect(isCellBlack(letterCell)).toBe(false);
    });
  });

  describe('writeLetterInCell', () => {
    it('should return a new cell with uppercase letter', () => {
      const result = writeLetterInCell(letterCell, 'a');
      expect(result).not.toBe(letterCell);
      expect(result.letter!.value).toBe('A');
      expect(letterCell.letter!.value).toBe(''); // original unchanged
    });

    it('should take only the first character', () => {
      const result = writeLetterInCell(letterCell, 'abc');
      expect(result.letter!.value).toBe('A');
    });

    it('should return same cell if type is not LETTER', () => {
      const result = writeLetterInCell(blackCell, 'a');
      expect(result).toBe(blackCell);
    });

    it('should handle empty string', () => {
      const result = writeLetterInCell(letterCell, '');
      expect(result.letter!.value).toBe('');
    });

    it('should preserve separator', () => {
      const cell: Cell = {
        x: 0, y: 0, type: 'LETTER',
        letter: { value: '', separator: 'LEFT' },
      };
      const result = writeLetterInCell(cell, 'b');
      expect(result.letter!.separator).toBe('LEFT');
    });
  });

  describe('writeNumberInCell', () => {
    it('should set number on a LETTER cell', () => {
      const result = writeNumberInCell(letterCell, 5);
      expect(result.letter!.number).toBe(5);
      expect(result).not.toBe(letterCell);
    });

    it('should return same cell if type is not LETTER', () => {
      const result = writeNumberInCell(blackCell, 5);
      expect(result).toBe(blackCell);
    });

    it('should clear number when undefined is passed', () => {
      const cell: Cell = {
        x: 0, y: 0, type: 'LETTER',
        letter: { value: 'A', separator: 'NONE', number: 3 },
      };
      const result = writeNumberInCell(cell, undefined);
      expect(result.letter!.number).toBeUndefined();
    });
  });

  describe('setLetterInCell', () => {
    it('should convert any cell to LETTER type', () => {
      const result = setLetterInCell(blackCell);
      expect(result.type).toBe('LETTER');
      expect(result.letter).toEqual(DEFAULT_LETTER);
      expect(result.clues).toBeUndefined();
    });

    it('should return a new cell', () => {
      const result = setLetterInCell(letterCell);
      expect(result).not.toBe(letterCell);
    });
  });

  describe('setSingleClueInCell', () => {
    it('should convert cell to CLUE_SINGLE with one clue', () => {
      const result = setSingleClueInCell(letterCell);
      expect(result.type).toBe('CLUE_SINGLE');
      expect(result.clues).toHaveLength(1);
      expect(result.clues![0].direction).toBe('RIGHT');
      expect(result.clues![0].text).toBe('');
      expect(result.letter).toBeUndefined();
    });
  });

  describe('setDoubleClueInCell', () => {
    it('should convert cell to CLUE_DOUBLE with two clues', () => {
      const result = setDoubleClueInCell(letterCell);
      expect(result.type).toBe('CLUE_DOUBLE');
      expect(result.clues).toHaveLength(2);
      expect(result.clues![0].direction).toBe('RIGHT');
      expect(result.clues![1].direction).toBe('DOWN');
      expect(result.letter).toBeUndefined();
    });
  });

  describe('setBlackInCell', () => {
    it('should convert cell to BLACK', () => {
      const result = setBlackInCell(letterCell);
      expect(result.type).toBe('BLACK');
      expect(result.letter).toBeUndefined();
      expect(result.clues).toBeUndefined();
    });
  });

  describe('setSeparatorInCell', () => {
    it('should set separator on a LETTER cell', () => {
      const result = setSeparatorInCell(letterCell, 'LEFT');
      expect(result.letter!.separator).toBe('LEFT');
    });

    it('should return same cell if type is not LETTER', () => {
      const result = setSeparatorInCell(blackCell, 'LEFT');
      expect(result).toBe(blackCell);
    });

    it('should preserve letter value', () => {
      const cell: Cell = {
        x: 0, y: 0, type: 'LETTER',
        letter: { value: 'Z', separator: 'NONE' },
      };
      const result = setSeparatorInCell(cell, 'BOTH');
      expect(result.letter!.value).toBe('Z');
      expect(result.letter!.separator).toBe('BOTH');
    });
  });
});
