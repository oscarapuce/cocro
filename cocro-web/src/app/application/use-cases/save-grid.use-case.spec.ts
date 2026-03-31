import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { SaveGridUseCase } from './save-grid.use-case';
import { GridPort, GRID_PORT } from '@application/ports/grid/grid.port';
import { EditorDraftPort, EDITOR_DRAFT_PORT } from '@application/ports/editor/editor-draft.port';
import { Grid } from '@domain/models/grid.model';

describe('SaveGridUseCase', () => {
  let useCase: SaveGridUseCase;
  let mockGridPort: jest.Mocked<GridPort>;
  let mockEditorDraft: jest.Mocked<EditorDraftPort>;

  const GRID_STUB: Grid = {
    id: 'grid-1',
    title: 'Ma grille',
    reference: 'Ref-1',
    difficulty: '2',
    description: 'Description',
    width: 5,
    height: 5,
    cells: [
      { x: 0, y: 0, type: 'LETTER', letter: { value: 'A', separator: 'NONE' } },
    ],
    globalClue: { label: 'Enigma', wordLengths: [3, 2] },
  };

  beforeEach(() => {
    mockGridPort = {
      getGrid: jest.fn(),
      getMyGrids: jest.fn(),
      submitGrid: jest.fn(),
      patchGrid: jest.fn(),
    };
    mockEditorDraft = {
      save: jest.fn(),
      load: jest.fn(),
      clear: jest.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        SaveGridUseCase,
        { provide: GRID_PORT, useValue: mockGridPort },
        { provide: EDITOR_DRAFT_PORT, useValue: mockEditorDraft },
      ],
    });

    useCase = TestBed.inject(SaveGridUseCase);
  });

  it('should be created', () => {
    expect(useCase).toBeTruthy();
  });

  describe('create', () => {
    it('should build SubmitGridRequest from Grid and call submitGrid', async () => {
      mockGridPort.submitGrid.mockReturnValue(of({ gridId: 'new-id' }));

      const result = await useCase.create(GRID_STUB);

      expect(result).toBe('new-id');
      expect(mockGridPort.submitGrid).toHaveBeenCalledWith(
        expect.objectContaining({
          title: 'Ma grille',
          reference: 'Ref-1',
          difficulty: '2',
          description: 'Description',
          width: 5,
          height: 5,
          globalClueLabel: 'Enigma',
          globalClueWordLengths: [3, 2],
        }),
      );
    });

    it('should clear the editor draft after successful create', async () => {
      mockGridPort.submitGrid.mockReturnValue(of({ gridId: 'new-id' }));

      await useCase.create(GRID_STUB);

      expect(mockEditorDraft.clear).toHaveBeenCalledTimes(1);
    });

    it('should map cells through cellToDto', async () => {
      mockGridPort.submitGrid.mockReturnValue(of({ gridId: 'new-id' }));

      await useCase.create(GRID_STUB);

      const call = mockGridPort.submitGrid.mock.calls[0][0];
      expect(call.cells[0]).toEqual(
        expect.objectContaining({ x: 0, y: 0, type: 'LETTER', letter: 'A' }),
      );
    });

    it('should default difficulty to NONE when undefined', async () => {
      mockGridPort.submitGrid.mockReturnValue(of({ gridId: 'new-id' }));
      const gridNoDifficulty: Grid = { ...GRID_STUB, difficulty: undefined };

      await useCase.create(gridNoDifficulty);

      const call = mockGridPort.submitGrid.mock.calls[0][0];
      expect(call.difficulty).toBe('NONE');
    });
  });

  describe('update', () => {
    it('should build PatchGridRequest from Grid and call patchGrid', async () => {
      mockGridPort.patchGrid.mockReturnValue(of(undefined));

      await useCase.update(GRID_STUB);

      expect(mockGridPort.patchGrid).toHaveBeenCalledWith(
        expect.objectContaining({
          gridId: 'grid-1',
          title: 'Ma grille',
          reference: 'Ref-1',
          difficulty: '2',
          width: 5,
          height: 5,
          globalClueLabel: 'Enigma',
          globalClueWordLengths: [3, 2],
        }),
      );
    });

    it('should not clear editor draft on update', async () => {
      mockGridPort.patchGrid.mockReturnValue(of(undefined));

      await useCase.update(GRID_STUB);

      expect(mockEditorDraft.clear).not.toHaveBeenCalled();
    });
  });
});
