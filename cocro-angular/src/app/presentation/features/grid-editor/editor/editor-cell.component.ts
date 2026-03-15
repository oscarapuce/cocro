import { Component, computed, inject, Input, Output, EventEmitter } from '@angular/core';
import { Cell } from '@domain/models/grid.model';
import { isCellLetter } from '@domain/services/cell-utils';
import { GridSelectorService } from '@application/services/grid-selector.service';

@Component({
  selector: 'app-editor-cell',
  standalone: true,
  template: `
    <div
      class="ec"
      [class.ec--letter]="cell.type === 'LETTER'"
      [class.ec--black]="cell.type === 'BLACK'"
      [class.ec--clue-single]="cell.type === 'CLUE_SINGLE'"
      [class.ec--clue-double]="cell.type === 'CLUE_DOUBLE'"
      [class.ec--selected]="isSelected()"
      [class.ec--on-direction]="isOnDirection()"
      [class.ec--separator-left]="hasLeftSeparator"
      [class.ec--separator-up]="hasUpSeparator"
      (click)="cellClick.emit()"
      [attr.title]="cellTitle"
    >
      @switch (cell.type) {
        @case ('LETTER') {
          <span class="ec__letter">{{ cell.letter?.value }}</span>
          @if (cell.letter?.number) {
            <span class="ec__number">{{ cell.letter?.number }}</span>
          }
        }
        @case ('BLACK') { }
        @case ('CLUE_SINGLE') {
          <span class="ec__clue-icon">→</span>
          <span class="ec__clue-text">{{ cell.clues?.[0]?.text?.slice(0, 12) }}</span>
        }
        @case ('CLUE_DOUBLE') {
          <span class="ec__clue-icon">↗</span>
          <span class="ec__clue-text">{{ cell.clues?.[0]?.text?.slice(0, 8) }}</span>
        }
      }
    </div>
  `,
  styleUrl: './editor-cell.component.scss',
})
export class EditorCellComponent {
  @Input() cell!: Cell;
  @Output() cellClick = new EventEmitter<void>();

  private selector = inject(GridSelectorService);

  isSelected = computed(() =>
    this.selector.selectedX() === this.cell.x && this.selector.selectedY() === this.cell.y,
  );

  isOnDirection = computed(() => {
    const dir = this.selector.direction();
    if (dir === 'NONE' || !isCellLetter(this.cell)) return false;
    const sx = this.selector.selectedX();
    const sy = this.selector.selectedY();
    if (dir === 'DOWNWARDS') return this.cell.x === sx && this.cell.y > sy;
    if (dir === 'RIGHTWARDS') return this.cell.y === sy && this.cell.x > sx;
    return false;
  });

  get hasLeftSeparator(): boolean {
    return this.cell.letter?.separator === 'LEFT' || this.cell.letter?.separator === 'BOTH';
  }

  get hasUpSeparator(): boolean {
    return this.cell.letter?.separator === 'UP' || this.cell.letter?.separator === 'BOTH';
  }

  get cellTitle(): string {
    const labels: Record<string, string> = {
      LETTER: 'Lettre', BLACK: 'Case noire',
      CLUE_SINGLE: 'Définition simple', CLUE_DOUBLE: 'Définition double',
    };
    return labels[this.cell.type] ?? '';
  }
}
