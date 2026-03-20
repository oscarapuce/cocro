import { NgStyle } from '@angular/common';
import { Component, HostListener, inject } from '@angular/core';

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

  @HostListener('window:keydown', ['$event'])
  handleKey(event: KeyboardEvent) {
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
