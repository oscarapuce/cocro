import { Injectable } from '@angular/core';
import { Grid } from '@domain/models/grid.model';
import { EditorDraftPort } from '@application/ports/editor/editor-draft.port';

@Injectable({ providedIn: 'root' })
export class EditorDraftLocalStorageAdapter implements EditorDraftPort {
  private static readonly KEY = 'cocro_editor_draft';

  save(grid: Grid): void {
    localStorage.setItem(EditorDraftLocalStorageAdapter.KEY, JSON.stringify(grid));
  }

  load(): Grid | null {
    try {
      const raw = localStorage.getItem(EditorDraftLocalStorageAdapter.KEY);
      return raw ? (JSON.parse(raw) as Grid) : null;
    } catch {
      return null;
    }
  }

  clear(): void {
    localStorage.removeItem(EditorDraftLocalStorageAdapter.KEY);
  }
}
