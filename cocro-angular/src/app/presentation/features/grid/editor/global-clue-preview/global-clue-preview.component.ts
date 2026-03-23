import { Component, computed, inject } from '@angular/core';
import { GridSelectorService } from '@application/service/grid-selector.service';

@Component({
  selector: 'cocro-global-clue-preview',
  standalone: true,
  templateUrl: './global-clue-preview.component.html',
  styleUrls: ['./global-clue-preview.component.scss'],
})
export class GlobalCluePreviewComponent {
  private readonly selectorService = inject(GridSelectorService);

  readonly previewWords = computed<{ letter: string; index: number }[][]>(() => {
    const grid = this.selectorService.grid();
    const wordLengths = grid.globalClue?.wordLengths;
    if (!wordLengths?.length) return [];

    const letterByNumber = new Map<number, string>();
    for (const cell of grid.cells) {
      if (cell.letter?.number != null && cell.letter.value) {
        letterByNumber.set(cell.letter.number, cell.letter.value);
      }
    }

    const words: { letter: string; index: number }[][] = [];
    let offset = 0;
    for (const length of wordLengths) {
      const word: { letter: string; index: number }[] = [];
      for (let i = 1; i <= length; i++) {
        word.push({ letter: letterByNumber.get(offset + i) ?? '', index: offset + i });
      }
      offset += length;
      words.push(word);
    }
    return words;
  });
}
