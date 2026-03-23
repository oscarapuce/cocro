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

  /**
   * Returns an array of words, each word being an array of letter strings.
   * Empty string means the corresponding numbered cell has no letter yet.
   * If no global clue or no word lengths defined, returns [].
   */
  readonly previewWords = computed<string[][]>(() => {
    const grid = this.selectorService.grid();
    const wordLengths = grid.globalClue?.wordLengths;
    if (!wordLengths?.length) return [];

    // Build number → letter value map from all cells
    const letterByNumber = new Map<number, string>();
    for (const cell of grid.cells) {
      if (cell.letter?.number != null && cell.letter.value) {
        letterByNumber.set(cell.letter.number, cell.letter.value);
      }
    }

    const words: string[][] = [];
    let offset = 0;
    for (const length of wordLengths) {
      const word: string[] = [];
      for (let i = 1; i <= length; i++) {
        word.push(letterByNumber.get(offset + i) ?? '');
      }
      offset += length;
      words.push(word);
    }
    return words;
  });
}
