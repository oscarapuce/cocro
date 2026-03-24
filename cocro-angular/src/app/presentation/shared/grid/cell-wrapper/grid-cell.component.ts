import { Component, computed, inject, Input } from '@angular/core';

import { Cell, Clue } from '@domain/models/grid.model';
import { LetterInputComponent } from '@presentation/shared/grid/inputs/letter/letter-input.component';
import { ClueInputComponent } from '@presentation/shared/grid/inputs/clues/clue-wrapper/clue-input.component';
import { GridSelectorService } from '@application/service/grid-selector.service';
import { isCellClue, isCellLetter } from '@domain/services/cell-utils.service';

@Component({
  selector: 'cocro-grid-cell',
  standalone: true,
  templateUrl: './grid-cell.component.html',
  imports: [
    LetterInputComponent,
    ClueInputComponent,
  ],
  styleUrls: ['./grid-cell.component.scss']
})
export class GridCellComponent {
  @Input() cell!: Cell;
  @Input() letterColorClass = '';

  private selector = inject(GridSelectorService);

  selected = computed(() => {
    return this.selector.selectedX() === this.cell.x && this.selector.selectedY() === this.cell.y;
  });

  isOnDirection = computed(() => {
    const dir = this.selector.direction();
    if (dir === 'NONE') return false;
    if (!isCellLetter(this.cell)) return false;

    const posX = this.selector.selectedX();
    const posY = this.selector.selectedY();

    if (dir === 'DOWNWARDS') {
      return this.cell.x === posX && this.cell.y > posY;
    } else if (dir === 'RIGHTWARDS') {
      return this.cell.y === posY && this.cell.x > posX;
    }
    return false;
  });

  onClick() {
    this.selector.selectOnClick(this.cell.x, this.cell.y);
  }

  isClue(): boolean {
    return isCellClue(this.cell);
  }

  isLetter(): boolean {
    return isCellLetter(this.cell);
  }

  getClues(): Clue[] {
    if (!this.cell.clues) throw new Error('cell.clues is undefined');
    return this.cell.clues;
  }

  hasLeftSeparator(): boolean {
    if (!this.cell.letter) return false;
    return this.cell.letter.separator === 'LEFT'
      || this.cell.letter.separator === 'BOTH';
  }

  hasUpSeparator(): boolean {
    if (!this.cell.letter) return false;
    return this.cell.letter.separator === 'UP'
      || this.cell.letter.separator === 'BOTH';
  }
}
