import { Component, Input } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Cell, Letter } from '@domain/models/grid.model';
import { getSepKeysFromSeparator, SepKey, toggleSeparatorKey } from '@domain/services/cell-utils.service';

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
    return getSepKeysFromSeparator(this.cell?.letter?.separator ?? 'NONE');
  }

  isSepActive(key: SepKey): boolean {
    return this.selectedSepKeys.includes(key);
  }

  toggleSep(key: SepKey): void {
    this.ensureLetter();
    this.cell.letter!.separator = toggleSeparatorKey(
      this.cell.letter!.separator ?? 'NONE',
      key
    );
  }

  onNumberChange(value: number | null): void {
    this.ensureLetter();
    this.cell.letter!.number = value == null || value < 1 ? undefined : value;
  }

  clearNumber(): void {
    this.ensureLetter();
    this.cell.letter!.number = undefined;
  }

  private ensureLetter(): void {
    if (!this.cell.letter) {
      const fallback: Letter = { value: '', separator: 'NONE' };
      this.cell.letter = fallback;
    }
  }
}
