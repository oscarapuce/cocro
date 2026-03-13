import { Component, Input, Output, EventEmitter } from '@angular/core';
import { SlicePipe } from '@angular/common';
import { EditorCell, EditorTool } from './grid-editor.component';

@Component({
  selector: 'app-editor-cell',
  standalone: true,
  imports: [SlicePipe],
  template: `
    <div
      class="ec"
      [class]="cellClass"
      (click)="click.emit(cell)"
      [attr.title]="cellTitle"
      [attr.aria-label]="'Case ' + cell.x + ',' + cell.y"
    >
      @switch (cell.type) {
        @case ('LETTER') {
          <span class="ec__letter">{{ cell.letter }}</span>
          @if (cell.number) {
            <span class="ec__number">{{ cell.number }}</span>
          }
        }
        @case ('BLACK') {
          <!-- black cell, no content -->
        }
        @case ('CLUE_SINGLE') {
          <span class="ec__clue-icon">→</span>
          <span class="ec__clue-text">{{ cell.clues[0]?.text | slice: 0 : 12 }}</span>
        }
        @case ('CLUE_DOUBLE') {
          <span class="ec__clue-icon">↗</span>
          <span class="ec__clue-text">{{ cell.clues[0]?.text | slice: 0 : 8 }}</span>
        }
      }
    </div>
  `,
  styleUrl: './editor-cell.component.scss',
})
export class EditorCellComponent {
  @Input() cell!: EditorCell;
  @Input() selected = false;
  @Input() tool: EditorTool = 'SELECT';
  @Output() click = new EventEmitter<EditorCell>();

  get cellClass(): string {
    const base = `ec--${this.cell.type.toLowerCase().replace('_', '-')}`;
    const sel = this.selected ? 'ec--selected' : '';
    return `${base} ${sel}`.trim();
  }

  get cellTitle(): string {
    const typeLabels: Record<string, string> = {
      LETTER: 'Lettre',
      BLACK: 'Case noire',
      CLUE_SINGLE: 'Définition simple',
      CLUE_DOUBLE: 'Définition double',
    };
    return typeLabels[this.cell.type] ?? '';
  }
}
