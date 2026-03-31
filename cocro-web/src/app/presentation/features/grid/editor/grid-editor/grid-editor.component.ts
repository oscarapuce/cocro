import { Component, computed, effect, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { createEmptyGrid } from '@domain/services/grid-utils.service';
import { EDITOR_DRAFT_PORT } from '@application/ports/editor/editor-draft.port';
import { LoadGridUseCase } from '@application/use-cases/load-grid.use-case';
import { SaveGridUseCase } from '@application/use-cases/save-grid.use-case';

import { GridComponent } from '@presentation/shared/grid/grid-wrapper/grid.component';
import { GridSelectorService } from '@application/service/grid-selector.service';
import { CardComponent } from '@presentation/shared/components/card/card.component';
import { ButtonComponent } from '@presentation/shared/components/button/button.component';
import { ToastService } from '@presentation/shared/components/toast/toast.service';
import { ClueEditorComponent } from '@presentation/features/grid/editor/clue-editor/clue-editor.component';
import { LetterEditorComponent } from '@presentation/features/grid/editor/letter-editor/letter-editor.component';
import { GridParamsComponent } from '@presentation/features/grid/editor/grid-params/grid-params.component';
import { CellTypeComponent } from '@presentation/features/grid/editor/cell-type/cell-type.component';
import { GlobalClueEditorComponent } from '@presentation/features/grid/editor/global-clue-editor/global-clue-editor.component';
import { GlobalCluePreviewComponent } from '@presentation/features/grid/editor/global-clue-preview/global-clue-preview.component';

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
    GlobalClueEditorComponent,
    GlobalCluePreviewComponent,
  ],
  templateUrl: './grid-editor.component.html',
  styleUrls: ['./grid-editor.component.scss'],
})
export class GridEditorComponent {
  private readonly loadGridUseCase = inject(LoadGridUseCase);
  private readonly saveGridUseCase = inject(SaveGridUseCase);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  readonly selectorService = inject(GridSelectorService);
  private readonly toast = inject(ToastService);
  private readonly draft = inject(EDITOR_DRAFT_PORT);

  readonly saving = signal(false);
  readonly showGlobalClue = signal(false);
  readonly isEditMode = signal(false);

  readonly submitLabel = computed(() => this.isEditMode() ? 'Enregistrer' : 'Créer la grille');

  toggleGlobalClue(): void {
    if (this.showGlobalClue()) {
      this.selectorService.clearEnigmaData();
    }
    this.showGlobalClue.update(v => !v);
  }

  readonly isClueSelected = computed(() => {
    const type = this.selectorService.selectedCell()?.type;
    return type === 'CLUE_SINGLE' || type === 'CLUE_DOUBLE';
  });

  readonly isLetterSelected = computed(() => {
    return this.selectorService.selectedCell()?.type === 'LETTER';
  });

  constructor() {
    const gridId = this.route.snapshot.paramMap.get('gridId');

    if (gridId) {
      this.isEditMode.set(true);
      this.selectorService.initGrid(createEmptyGrid('0', 'Chargement...', 10, 13));
      firstValueFrom(this.loadGridUseCase.execute(gridId)).then((grid) => {
        this.selectorService.initGrid(grid);
      }).catch(() => {
        this.toast.error('Grille introuvable.');
        this.router.navigate(['/grid/mine']);
      });
    } else {
      const draft = this.draft.load();
      this.selectorService.initGrid(draft ?? createEmptyGrid('0', 'Nouvelle grille', 10, 13));
      effect(() => {
        this.draft.save(this.selectorService.grid());
      });
    }
  }

  resetDraft(): void {
    this.draft.clear();
    this.selectorService.initGrid(createEmptyGrid('0', 'Nouvelle grille', 10, 13));
    this.showGlobalClue.set(false);
  }

  async onSubmit() {
    this.saving.set(true);
    try {
      const grid = this.selectorService.grid();

      if (this.isEditMode()) {
        await this.saveGridUseCase.update(grid);
        this.toast.success('Grille mise à jour.');
        await this.router.navigate(['/grid/mine']);
      } else {
        await this.saveGridUseCase.create(grid);
        this.toast.success('Grille créée avec succès !');
        this.router.navigate(['/grid/mine']);
      }
    } catch (e: any) {
      const message = e.message ? e.message : 'Erreur inconnue';
      this.toast.error(message);
    } finally {
      this.saving.set(false);
    }
  }
}
