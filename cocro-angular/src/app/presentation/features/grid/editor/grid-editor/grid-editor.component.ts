import { Component, computed, effect, inject, signal } from '@angular/core';
import { createEmptyGrid } from '@domain/services/grid-utils.service';
import { EDITOR_DRAFT_PORT } from '@application/ports/editor/editor-draft.port';

import { GridComponent } from '@presentation/shared/grid/grid-wrapper/grid.component';
import { GridSelectorService } from '@application/service/grid-selector.service';
import { CardComponent } from '@presentation/shared/components/card/card.component';
import { ButtonComponent } from '@presentation/shared/components/button/button.component';
import { ToastService } from '@presentation/shared/components/toast/toast.service';
import { ClueEditorComponent } from '@presentation/features/grid/editor/clue-editor/clue-editor.component';
import { LetterEditorComponent } from '@presentation/features/grid/editor/letter-editor/letter-editor.component';
import { GridParamsComponent } from '@presentation/features/grid/editor/grid-params/grid-params.component';
import { CellTypeComponent } from '@presentation/features/grid/editor/cell-type/cell-type.component';
import { CreateGridUseCase } from '@application/use-cases/create-grid.use-case';
import { cellToDto, SubmitGridRequest } from '@application/dto/grid.dto';

@Component({
  selector: 'cocro-grid-editor',
  standalone: true,
  imports: [
    GridComponent,
    CardComponent,
    ButtonComponent,
    ClueEditorComponent,
    LetterEditorComponent,
    GridParamsComponent,
    CellTypeComponent,
  ],
  templateUrl: './grid-editor.component.html',
  styleUrls: ['./grid-editor.component.scss'],
})
export class GridEditorComponent {
  private readonly createGridUseCase = inject(CreateGridUseCase);
  readonly selectorService = inject(GridSelectorService);
  private readonly toast = inject(ToastService);
  private readonly draft = inject(EDITOR_DRAFT_PORT); // lecture + auto-save uniquement

  readonly saving = signal(false);

  readonly isClueSelected = computed(() => {
    const type = this.selectorService.selectedCell()?.type;
    return type === 'CLUE_SINGLE' || type === 'CLUE_DOUBLE';
  });

  readonly isLetterSelected = computed(() => {
    return this.selectorService.selectedCell()?.type === 'LETTER';
  });

  constructor() {
    const draft = this.draft.load();
    this.selectorService.initGrid(draft ?? createEmptyGrid('0', 'Nouvelle grille', 10, 8));

    effect(() => {
      this.draft.save(this.selectorService.grid());
    });
  }

  async onSubmit() {
    this.saving.set(true);
    try {
      const grid = this.selectorService.grid();
      const request: SubmitGridRequest = {
        gridId: grid.id,
        title: grid.title,
        difficulty: grid.difficulty ?? 'NONE',
        description: grid.description,
        width: grid.width,
        height: grid.height,
        cells: grid.cells.map(cellToDto),
      };
      const id = await this.createGridUseCase.execute(request);
      this.toast.success(`Grille creee avec succes (ID: ${id})`);
    } catch (e: any) {
      const message = e.message ? e.message : 'Erreur inconnue';
      this.toast.error(message);
    } finally {
      this.saving.set(false);
    }
  }
}
