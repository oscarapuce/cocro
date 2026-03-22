import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { GridSelectorService } from '@application/service/grid-selector.service';
import { GridDifficulty } from '@domain/models/grid.model';
import { ButtonComponent } from '@presentation/shared/components/button/button.component';

@Component({
  selector: 'cocro-grid-params',
  standalone: true,
  imports: [FormsModule, ButtonComponent],
  templateUrl: './grid-params.component.html',
  styleUrls: ['./grid-params.component.scss'],
})
export class GridParamsComponent {
  readonly selectorService = inject(GridSelectorService);

  readonly DIFFICULTIES: { value: GridDifficulty; label: string }[] = [
    { value: 'NONE', label: '–' },
    { value: '0',   label: '0' },
    { value: '1',   label: '1' },
    { value: '2',   label: '2' },
    { value: '3',   label: '3' },
    { value: '4',   label: '4' },
    { value: '5',   label: '5' },
    { value: '0-1', label: '0-1' },
    { value: '1-2', label: '1-2' },
    { value: '2-3', label: '2-3' },
    { value: '3-4', label: '3-4' },
    { value: '4-5', label: '4-5' },
  ];

  updateDifficulty(value: string): void {
    this.selectorService.updateDifficulty(value as GridDifficulty);
  }

  onAddRow() {
    const grid = this.selectorService.grid();
    this.selectorService.onResize(grid.width, grid.height + 1);
  }

  onRemoveRow() {
    const grid = this.selectorService.grid();
    this.selectorService.onResize(grid.width, grid.height - 1);
  }

  onAddColumn() {
    const grid = this.selectorService.grid();
    this.selectorService.onResize(grid.width + 1, grid.height);
  }

  onRemoveColumn() {
    const grid = this.selectorService.grid();
    this.selectorService.onResize(grid.width - 1, grid.height);
  }
}
