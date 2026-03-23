import { Component, computed, inject, Input } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Cell, Letter } from '@domain/models/grid.model';
import { getSepKeysFromSeparator, SepKey, toggleSeparatorKey } from '@domain/services/cell-utils.service';
import { GridSelectorService } from '@application/service/grid-selector.service';

@Component({
  selector: 'cocro-letter-editor',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './letter-editor.component.html',
  styleUrls: ['./letter-editor.component.scss']
})
export class LetterEditorComponent {
  @Input() cell!: Cell;

  private readonly selectorService = inject(GridSelectorService);

  readonly maxIndex = computed(() => {
    const wl = this.selectorService.grid().globalClue?.wordLengths;
    return wl?.length ? wl.reduce((a, b) => a + b, 0) : 99;
  });

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
    this.selectorService.updateCellInGrid(this.cell);
  }

  onNumberChange(value: number | null): void {
    this.ensureLetter();
    const max = this.maxIndex();
    const clamped = value == null || value < 1 ? undefined : Math.min(value, max);
    this.cell.letter!.number = clamped;
    this.selectorService.updateCellInGrid(this.cell);
  }

  incrementNumber(): void {
    this.ensureLetter();
    const current = this.cell.letter!.number ?? 0;
    this.cell.letter!.number = Math.min(this.maxIndex(), current + 1);
    this.selectorService.updateCellInGrid(this.cell);
  }

  decrementNumber(): void {
    this.ensureLetter();
    const current = this.cell.letter!.number;
    if (current === undefined || current <= 1) {
      this.cell.letter!.number = undefined;
    } else {
      this.cell.letter!.number = current - 1;
    }
    this.selectorService.updateCellInGrid(this.cell);
  }

  clearNumber(): void {
    this.ensureLetter();
    this.cell.letter!.number = undefined;
    this.selectorService.updateCellInGrid(this.cell);
  }

  private ensureLetter(): void {
    if (!this.cell.letter) {
      const fallback: Letter = { value: '', separator: 'NONE' };
      this.cell.letter = fallback;
    }
  }
}
