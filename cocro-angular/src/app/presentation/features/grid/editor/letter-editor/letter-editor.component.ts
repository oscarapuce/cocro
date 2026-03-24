import { Component, computed, inject, Input } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Cell } from '@domain/models/grid.model';
import {
  getSepKeysFromSeparator,
  setSeparatorInCell,
  SepKey,
  toggleSeparatorKey,
  writeNumberInCell,
} from '@domain/services/cell-utils.service';
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

  readonly usedNumbers = computed(() => new Set(
    this.selectorService.grid().cells
      .filter(c => c.type === 'LETTER' && c.letter?.number != null)
      .map(c => c.letter!.number!)
  ));

  get selectedSepKeys(): SepKey[] {
    return getSepKeysFromSeparator(this.cell?.letter?.separator ?? 'NONE');
  }

  isSepActive(key: SepKey): boolean {
    return this.selectedSepKeys.includes(key);
  }

  toggleSep(key: SepKey): void {
    const newSep = toggleSeparatorKey(this.cell.letter?.separator ?? 'NONE', key);
    this.selectorService.updateCellInGrid(setSeparatorInCell(this.cell, newSep));
  }

  onNumberChange(value: number | null): void {
    const clamped = value == null || value < 1 ? undefined : Math.min(value, this.maxIndex());
    if (clamped != null && clamped !== this.cell.letter?.number && this.usedNumbers().has(clamped)) {
      return;
    }
    this.selectorService.updateCellInGrid(writeNumberInCell(this.cell, clamped));
  }

  incrementNumber(): void {
    const used = this.usedNumbers();
    const max = this.maxIndex();
    const own = this.cell.letter?.number;
    let next = (own ?? 0) + 1;
    while (next <= max && used.has(next) && next !== own) {
      next++;
    }
    if (next > max) return;
    this.selectorService.updateCellInGrid(writeNumberInCell(this.cell, next));
  }

  decrementNumber(): void {
    const used = this.usedNumbers();
    const own = this.cell.letter?.number;
    if (own === undefined || own <= 1) {
      this.selectorService.updateCellInGrid(writeNumberInCell(this.cell, undefined));
      return;
    }
    let next = own - 1;
    while (next >= 1 && used.has(next) && next !== own) {
      next--;
    }
    const newVal = next < 1 ? undefined : next;
    this.selectorService.updateCellInGrid(writeNumberInCell(this.cell, newVal));
  }

  clearNumber(): void {
    this.selectorService.updateCellInGrid(writeNumberInCell(this.cell, undefined));
  }
}
