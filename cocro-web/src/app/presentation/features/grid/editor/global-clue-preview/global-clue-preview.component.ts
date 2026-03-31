import { Component, computed, inject } from '@angular/core';
import { GridSelectorService } from '@application/service/grid-selector.service';
import { buildGlobalCluePreview } from '@domain/services/grid-utils.service';

@Component({
  selector: 'cocro-global-clue-preview',
  standalone: true,
  templateUrl: './global-clue-preview.component.html',
  styleUrls: ['./global-clue-preview.component.scss'],
})
export class GlobalCluePreviewComponent {
  private readonly selectorService = inject(GridSelectorService);

  readonly previewWords = computed(() => buildGlobalCluePreview(this.selectorService.grid()));
}
