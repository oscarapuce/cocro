import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { GridPort, GRID_PORT } from '@application/ports/grid/grid.port';
import { EditorDraftPort, EDITOR_DRAFT_PORT } from '@application/ports/editor/editor-draft.port';
import { cellToDto, PatchGridRequest, SubmitGridRequest } from '@application/dto/grid.dto';
import { Grid } from '@domain/models/grid.model';

@Injectable({ providedIn: 'root' })
export class SaveGridUseCase {
  private readonly gridPort = inject<GridPort>(GRID_PORT);
  private readonly editorDraft = inject<EditorDraftPort>(EDITOR_DRAFT_PORT);

  async create(grid: Grid): Promise<string> {
    const request: SubmitGridRequest = {
      title: grid.title,
      reference: grid.reference,
      difficulty: grid.difficulty ?? 'NONE',
      description: grid.description,
      width: grid.width,
      height: grid.height,
      cells: grid.cells.map(cellToDto),
      globalClueLabel: grid.globalClue?.label,
      globalClueWordLengths: grid.globalClue?.wordLengths,
    };
    const response = await firstValueFrom(this.gridPort.submitGrid(request));
    this.editorDraft.clear();
    return response.gridId;
  }

  async update(grid: Grid): Promise<void> {
    const request: PatchGridRequest = {
      gridId: grid.id,
      title: grid.title,
      reference: grid.reference,
      difficulty: grid.difficulty ?? 'NONE',
      description: grid.description,
      width: grid.width,
      height: grid.height,
      cells: grid.cells.map(cellToDto),
      globalClueLabel: grid.globalClue?.label,
      globalClueWordLengths: grid.globalClue?.wordLengths,
    };
    await firstValueFrom(this.gridPort.patchGrid(request));
  }
}
