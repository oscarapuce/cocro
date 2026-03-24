import { NgStyle } from '@angular/common';
import { Component, HostListener, inject, Input } from '@angular/core';

import { GridCellComponent } from '@presentation/shared/grid/cell-wrapper/grid-cell.component';
import { GridSelectorService } from '@application/service/grid-selector.service';

@Component({
  selector: 'cocro-grid',
  standalone: true,
  templateUrl: './grid.component.html',
  imports: [
    GridCellComponent,
    NgStyle
  ],
  styleUrls: ['./grid.component.scss']
})
export class GridComponent {
  readonly selector = inject(GridSelectorService);

  /** Disable keyboard handling (for game board which manages its own keyboard). */
  @Input() disableKeyboard = false;
  /** Optional callback to compute a CSS class for a letter cell (used for player coloring). */
  @Input() cellColorClassFn: ((x: number, y: number) => string) | null = null;

  @HostListener('window:keydown', ['$event'])
  handleKey(event: KeyboardEvent) {
    if (this.disableKeyboard) return;

    const target = event.target as HTMLElement;
    const tag = target.tagName.toLowerCase();

    const isTyping = tag === 'input' || tag === 'textarea' || target.isContentEditable;
    if (isTyping) return;

    const hasModifier =
      event.ctrlKey ||
      event.metaKey ||
      event.altKey ||
      event.getModifierState?.('AltGraph');

    if (
      hasModifier ||
      event.key === 'Control' ||
      event.key === 'Meta' ||
      event.key === 'Alt' ||
      event.key === 'AltGraph'
    ) {
      return;
    }

    switch (event.key) {
      case 'ArrowRight':
        this.selector.moveRight();
        break;
      case 'ArrowLeft':
        this.selector.moveLeft();
        break;
      case 'ArrowDown':
        this.selector.moveDown();
        break;
      case 'ArrowUp':
        this.selector.moveUp();
        break;
      case 'Backspace':
        this.selector.handleBackspace();
        event.preventDefault();
        break;
      case 'Delete':
        this.selector.handleDelete();
        event.preventDefault();
        break;
      case 'Shift':
        this.selector.handleShift();
        break;
      default:
        if (/^[a-zA-Z]$/.test(event.key)) {
          this.selector.inputLetter(event.key);
          event.preventDefault();
        }
    }
  }
}
