import { EditorDraftLocalStorageAdapter } from './editor-draft-local-storage.adapter';
import { Grid } from '@domain/models/grid.model';

const STORAGE_KEY = 'cocro_editor_draft';

const GRID_STUB: Grid = {
  id: 'draft-grid-1',
  title: 'My Draft',
  width: 10,
  height: 8,
  cells: [
    { x: 0, y: 0, type: 'LETTER', letter: { value: 'A', separator: 'NONE' } },
    { x: 1, y: 0, type: 'BLACK' },
  ],
  difficulty: '2',
};

describe('EditorDraftLocalStorageAdapter', () => {
  let adapter: EditorDraftLocalStorageAdapter;
  let localStorageMock: Record<string, string>;

  beforeEach(() => {
    // Mock localStorage with a clean in-memory store per test
    localStorageMock = {};

    Object.defineProperty(global, 'localStorage', {
      value: {
        getItem: jest.fn((key: string) => localStorageMock[key] ?? null),
        setItem: jest.fn((key: string, value: string) => {
          localStorageMock[key] = value;
        }),
        removeItem: jest.fn((key: string) => {
          delete localStorageMock[key];
        }),
        clear: jest.fn(() => {
          localStorageMock = {};
        }),
        length: 0,
        key: jest.fn(),
      },
      writable: true,
    });

    adapter = new EditorDraftLocalStorageAdapter();
  });

  it('should be created', () => {
    expect(adapter).toBeTruthy();
  });

  describe('save and load (round-trip)', () => {
    it('should save a grid and load it back', () => {
      adapter.save(GRID_STUB);
      const loaded = adapter.load();

      expect(loaded).toEqual(GRID_STUB);
    });

    it('should persist all grid fields faithfully', () => {
      adapter.save(GRID_STUB);
      const loaded = adapter.load()!;

      expect(loaded.id).toBe('draft-grid-1');
      expect(loaded.title).toBe('My Draft');
      expect(loaded.width).toBe(10);
      expect(loaded.height).toBe(8);
      expect(loaded.cells).toHaveLength(2);
      expect(loaded.difficulty).toBe('2');
    });

    it('should serialize to JSON in localStorage', () => {
      adapter.save(GRID_STUB);

      expect(localStorage.setItem).toHaveBeenCalledWith(
        STORAGE_KEY,
        JSON.stringify(GRID_STUB),
      );
    });

    it('should overwrite a previous draft on re-save', () => {
      const firstGrid: Grid = { ...GRID_STUB, title: 'First' };
      const secondGrid: Grid = { ...GRID_STUB, title: 'Second' };

      adapter.save(firstGrid);
      adapter.save(secondGrid);

      const loaded = adapter.load();
      expect(loaded?.title).toBe('Second');
    });
  });

  describe('load', () => {
    it('should return null when localStorage is empty', () => {
      const result = adapter.load();
      expect(result).toBeNull();
    });

    it('should return null when the key is not present', () => {
      localStorageMock['other-key'] = 'something';

      const result = adapter.load();
      expect(result).toBeNull();
    });

    it('should return null when the stored value is corrupted JSON', () => {
      localStorageMock[STORAGE_KEY] = 'not-valid-json{{{{';

      const result = adapter.load();
      expect(result).toBeNull();
    });

    it('should return null gracefully on any JSON parse error', () => {
      localStorageMock[STORAGE_KEY] = '{incomplete: ';

      const result = adapter.load();
      expect(result).toBeNull();
    });
  });

  describe('clear', () => {
    it('should remove the draft from localStorage', () => {
      adapter.save(GRID_STUB);
      adapter.clear();

      expect(localStorage.removeItem).toHaveBeenCalledWith(STORAGE_KEY);
    });

    it('should cause load to return null after clear', () => {
      adapter.save(GRID_STUB);
      adapter.clear();

      const result = adapter.load();
      expect(result).toBeNull();
    });

    it('should not throw when called on an empty store', () => {
      expect(() => adapter.clear()).not.toThrow();
    });
  });
});
