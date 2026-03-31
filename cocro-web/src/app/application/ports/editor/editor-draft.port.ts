import { InjectionToken } from '@angular/core';
import { Grid } from '@domain/models/grid.model';

export interface EditorDraftPort {
  save(grid: Grid): void;
  load(): Grid | null;
  clear(): void;
}

export const EDITOR_DRAFT_PORT = new InjectionToken<EditorDraftPort>('EDITOR_DRAFT_PORT');
