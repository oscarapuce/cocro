import { Component, computed, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { GridSelectorService } from '@application/service/grid-selector.service';
import { GlobalClue } from '@domain/models/grid.model';

@Component({
  selector: 'cocro-global-clue-editor',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './global-clue-editor.component.html',
  styleUrls: ['./global-clue-editor.component.scss'],
})
export class GlobalClueEditorComponent {
  readonly selectorService = inject(GridSelectorService);

  readonly numberedCellCount = computed(() =>
    this.selectorService.grid().cells.filter(c => c.letter?.number != null).length
  );

  readonly totalRequested = computed(() =>
    (this.globalClue?.wordLengths ?? []).reduce((a, b) => a + b, 0)
  );

  readonly countMismatch = computed(() =>
    (this.globalClue?.wordLengths?.length ?? 0) > 0 &&
    this.totalRequested() !== this.numberedCellCount()
  );

  get globalClue(): GlobalClue | undefined {
    return this.selectorService.grid().globalClue;
  }

  updateLabel(label: string): void {
    const current = this.globalClue;
    this.selectorService.updateGlobalClue({
      label,
      wordLengths: current?.wordLengths ?? [],
    });
  }

  incrementLength(index: number): void {
    const current = this.globalClue;
    if (!current) return;
    const wordLengths = [...current.wordLengths];
    wordLengths[index] = (wordLengths[index] ?? 1) + 1;
    this.selectorService.updateGlobalClue({ ...current, wordLengths });
  }

  decrementLength(index: number): void {
    const current = this.globalClue;
    if (!current) return;
    const wordLengths = [...current.wordLengths];
    if ((wordLengths[index] ?? 1) <= 1) return;
    wordLengths[index] = wordLengths[index] - 1;
    this.selectorService.updateGlobalClue({ ...current, wordLengths });
  }

  addWord(): void {
    const current = this.globalClue;
    const wordLengths = current ? [...current.wordLengths, 1] : [1];
    this.selectorService.updateGlobalClue({
      label: current?.label ?? '',
      wordLengths,
    });
  }

  removeWord(index: number): void {
    const current = this.globalClue;
    if (!current) return;
    const wordLengths = current.wordLengths.filter((_, i) => i !== index);
    this.selectorService.updateGlobalClue({ ...current, wordLengths });
  }
}
