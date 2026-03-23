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
    this.selectorService.updateCellInGrid(writeNumberInCell(this.cell, clamped));
  }

  incrementNumber(): void {
    const next = Math.min(this.maxIndex(), (this.cell.letter?.number ?? 0) + 1);
    this.selectorService.updateCellInGrid(writeNumberInCell(this.cell, next));
  }

  decrementNumber(): void {
    const current = this.cell.letter?.number;
    const next = current === undefined || current <= 1 ? undefined : current - 1;
    this.selectorService.updateCellInGrid(writeNumberInCell(this.cell, next));
  }

  clearNumber(): void {
    this.selectorService.updateCellInGrid(writeNumberInCell(this.cell, undefined));
  }
}
