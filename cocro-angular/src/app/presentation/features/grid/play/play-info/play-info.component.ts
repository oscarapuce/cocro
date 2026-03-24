import { Component, computed, inject } from '@angular/core';
import { GridSelectorService } from '@application/service/grid-selector.service';

@Component({
  selector: 'cocro-play-info',
  standalone: true,
  templateUrl: './play-info.component.html',
  styleUrl: './play-info.component.scss',
})
export class PlayInfoComponent {
  private readonly selector = inject(GridSelectorService);

  readonly grid = this.selector.grid;

  readonly hasDescription = computed(() => !!this.selector.grid().description);
  readonly hasGlobalClue = computed(() => !!this.selector.grid().globalClue?.label);

  readonly selectedCellClues = computed(() => {
    const cell = this.selector.selectedCell();
    if (!cell) return null;
    if (cell.type === 'CLUE_SINGLE' || cell.type === 'CLUE_DOUBLE') {
      return cell.clues ?? null;
    }
    return null;
  });

  readonly isClueSelected = computed(() => {
    const type = this.selector.selectedCell()?.type;
    return type === 'CLUE_SINGLE' || type === 'CLUE_DOUBLE';
  });
}
