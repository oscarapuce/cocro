import { Component, Input } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Cell, Letter, SeparatorType } from '@domain/models/grid.model';

type SepKey = 'left' | 'up';

@Component({
  selector: 'cocro-letter-editor',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './letter-editor.component.html',
  styleUrls: ['./letter-editor.component.scss']
})
export class LetterEditorComponent {
  @Input() cell!: Cell;

  get selectedSepKeys(): SepKey[] {
    const sep: SeparatorType = this.cell?.letter?.separator ?? 'NONE';
    switch (sep) {
      case 'LEFT': return ['left'];
      case 'UP':   return ['up'];
      case 'BOTH': return ['left', 'up'];
      default:     return [];
    }
  }

  isSepActive(key: SepKey): boolean {
    return this.selectedSepKeys.includes(key);
  }

  toggleSep(key: SepKey): void {
    this.ensureLetter();
    const current = new Set(this.selectedSepKeys);
    if (current.has(key)) {
      current.delete(key);
    } else {
      current.add(key);
    }

    const hasLeft = current.has('left');
    const hasUp   = current.has('up');

    this.cell.letter!.separator =
      hasLeft && hasUp ? 'BOTH' :
        hasLeft ? 'LEFT' :
          hasUp   ? 'UP' :
            'NONE';
  }

  private ensureLetter(): void {
    if (!this.cell.letter) {
      const fallback: Letter = { value: '', separator: 'NONE' };
      this.cell.letter = fallback;
    }
  }
}
