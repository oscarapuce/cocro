import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { GridSelectorService } from '@application/service/grid-selector.service';
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
