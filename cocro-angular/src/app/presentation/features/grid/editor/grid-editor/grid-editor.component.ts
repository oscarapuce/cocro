import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CellType } from '@domain/models/grid.model';
import { createEmptyGrid, withUpdatedCell } from '@domain/services/grid-utils.service';
import { isCellBlack, isCellClueDouble, isCellClueSingle, isCellLetter } from '@domain/services/cell-utils.service';

import { GridComponent } from '@presentation/shared/grid/grid-wrapper/grid.component';
import { GridSelectorService } from '@application/service/grid-selector.service';
import { CardComponent } from '@presentation/shared/components/card/card.component';
import { ButtonComponent } from '@presentation/shared/components/button/button.component';
import { ToastService } from '@presentation/shared/components/toast/toast.service';
import { ClueEditorComponent } from '@presentation/features/grid/editor/clue-editor/clue-editor.component';
import { LetterEditorComponent } from '@presentation/features/grid/editor/letter-editor/letter-editor.component';
import { CreateGridUseCase } from '@application/use-cases/create-grid.use-case';
import { cellToDto, SubmitGridRequest } from '@application/dto/grid.dto';

@Component({
  selector: 'cocro-grid-editor',
  standalone: true,
  imports: [
    GridComponent,
    CardComponent,
    ButtonComponent,
    FormsModule,
    ClueEditorComponent,
    LetterEditorComponent,
  ],
  templateUrl: './grid-editor.component.html',
  styleUrls: ['./grid-editor.component.scss']
})
export class GridEditorComponent {

  private readonly createGridUseCase = inject(CreateGridUseCase);
  readonly selectorService = inject(GridSelectorService);
  private readonly toast = inject(ToastService);

  readonly saving = signal(false);

  readonly selectedCellType = computed<CellType>(() => {
    const cell = this.selectorService.selectedCell();
    return cell?.type ?? 'LETTER';
  });

  readonly isClueSelected = computed(() => {
    const type = this.selectedCellType();
    return type === 'CLUE_SINGLE' || type === 'CLUE_DOUBLE';
  });

  readonly isLetterSelected = computed(() => {
    return this.selectedCellType() === 'LETTER';
  });

  constructor() {
    const grid = createEmptyGrid('0', 'Nouvelle grille', 10, 8);
    this.selectorService.initGrid(grid);
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

  onCellTypeChange(type: CellType) {
    this.selectorService.onCellTypeChange(type);

    // Blur the focused element so arrows go back to window
    const active = document.activeElement as HTMLElement;
    if (active) {
      active.blur();
    }
  }

  isCellLetterType(): boolean {
    const cell = this.selectorService.selectedCell();
    if (!cell) return false;
    return isCellLetter(cell);
  }

  isCellClueSingleType(): boolean {
    const cell = this.selectorService.selectedCell();
    if (!cell) return false;
    return isCellClueSingle(cell);
  }

  isCellClueDoubleType(): boolean {
    const cell = this.selectorService.selectedCell();
    if (!cell) return false;
    return isCellClueDouble(cell);
  }

  isCellBlackType(): boolean {
    const cell = this.selectorService.selectedCell();
    if (!cell) return false;
    return isCellBlack(cell);
  }

  onAddRow() {
    const grid = this.selectorService.grid();
    this.selectorService.onResize(grid.width, grid.height + 1);
  }

  onRemoveRow() {
    const grid = this.selectorService.grid();
    this.selectorService.onResize(grid.width, grid.height - 1);
  }

  onAddColumn() {
    const grid = this.selectorService.grid();
    this.selectorService.onResize(grid.width + 1, grid.height);
  }

  onRemoveColumn() {
    const grid = this.selectorService.grid();
    this.selectorService.onResize(grid.width - 1, grid.height);
  }
}
