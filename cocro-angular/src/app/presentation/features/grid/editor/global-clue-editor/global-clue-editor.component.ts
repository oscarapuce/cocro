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

  // Map number → letter, recomputed only when grid() changes
  readonly letterByNumber = computed(() => {
    const map = new Map<number, string>();
    for (const cell of this.selectorService.grid().cells) {
      if (cell.letter?.number !== undefined && cell.letter?.number !== null) {
        map.set(cell.letter.number, cell.letter.value || '_');
      }
    }
    return map;
  });

  getLetterForNumber(n: number): string {
    return this.letterByNumber().get(n) ?? '_';
  }

  isUnresolved(n: number): boolean {
    return !this.letterByNumber().has(n);
  }

  parseWord(raw: string): number[] {
    return raw.split(',')
      .map(s => parseInt(s.trim(), 10))
      .filter(n => !isNaN(n) && n > 0);
  }

  wordToRaw(word: number[]): string {
    return word.join(', ');
  }

  get globalClue(): GlobalClue | undefined {
    return this.selectorService.grid().globalClue;
  }

  updateLabel(label: string): void {
    const current = this.globalClue;
    this.selectorService.updateGlobalClue({
      label,
      words: current?.words ?? [],
    });
  }

  updateWord(index: number, raw: string): void {
    const current = this.globalClue;
    if (!current) return;
    const words = [...current.words];
    words[index] = this.parseWord(raw);
    this.selectorService.updateGlobalClue({ ...current, words });
  }

  addWord(): void {
    const current = this.globalClue;
    const words = current ? [...current.words, []] : [[]];
    this.selectorService.updateGlobalClue({
      label: current?.label ?? '',
      words,
    });
  }

  removeWord(index: number): void {
    const current = this.globalClue;
    if (!current) return;
    const words = current.words.filter((_, i) => i !== index);
    this.selectorService.updateGlobalClue({ ...current, words });
  }

  clearGlobalClue(): void {
    this.selectorService.updateGlobalClue(undefined);
  }
}
