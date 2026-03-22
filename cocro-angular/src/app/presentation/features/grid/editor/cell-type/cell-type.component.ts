import { Component, computed, inject } from '@angular/core';
import { CellType } from '@domain/models/grid.model';
import { isCellBlack, isCellClueDouble, isCellClueSingle, isCellLetter } from '@domain/services/cell-utils.service';
import { GridSelectorService } from '@application/service/grid-selector.service';
import { CardComponent } from '@presentation/shared/components/card/card.component';

@Component({
  selector: 'cocro-cell-type',
  standalone: true,
  imports: [CardComponent],
  templateUrl: './cell-type.component.html',
  styleUrls: ['./cell-type.component.scss'],
})
export class CellTypeComponent {
  readonly selectorService = inject(GridSelectorService);

  readonly selectedCellType = computed<CellType>(() => {
    const cell = this.selectorService.selectedCell();
    return cell?.type ?? 'LETTER';
  });

  onCellTypeChange(type: CellType) {
    this.selectorService.onCellTypeChange(type);
    const active = document.activeElement as HTMLElement;
    if (active) active.blur();
  }

  isCellLetterType(): boolean {
    const cell = this.selectorService.selectedCell();
    if (!cell) return false;
    return isCellLetter(cell);
  }

  isCellClueSingleType(): boolean {
    const cell = this.selectorService.selectedCell();
    if (!cell) return false;
    return isCellClueSingle(cell);
  }

  isCellClueDoubleType(): boolean {
    const cell = this.selectorService.selectedCell();
    if (!cell) return false;
    return isCellClueDouble(cell);
  }

  isCellBlackType(): boolean {
    const cell = this.selectorService.selectedCell();
    if (!cell) return false;
    return isCellBlack(cell);
  }
}
