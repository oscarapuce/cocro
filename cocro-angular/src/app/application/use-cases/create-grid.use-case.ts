import { Inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { GridPort, GRID_PORT } from '@application/ports/grid/grid.port';
import { EditorDraftPort, EDITOR_DRAFT_PORT } from '@application/ports/editor/editor-draft.port';
import { SubmitGridRequest } from '@application/dto/grid.dto';

@Injectable({ providedIn: 'root' })
export class CreateGridUseCase {
  constructor(
    @Inject(GRID_PORT) private gridPort: GridPort,
    @Inject(EDITOR_DRAFT_PORT) private editorDraft: EditorDraftPort,
  ) {}

  async execute(request: SubmitGridRequest): Promise<string> {
    const response = await firstValueFrom(this.gridPort.submitGrid(request));
    this.editorDraft.clear();
    return response.gridId;
  }
}
