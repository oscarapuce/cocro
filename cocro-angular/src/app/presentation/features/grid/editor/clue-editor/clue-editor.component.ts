import { Component, Input } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Cell, ClueDirection } from '@domain/models/grid.model';
import { ClueArrowComponent } from '@presentation/shared/grid/inputs/clues/arrow/clue-arrow.component';

@Component({
  selector: 'cocro-clue-editor',
  standalone: true,
  imports: [FormsModule, ClueArrowComponent],
  templateUrl: 'clue-editor.component.html',
  styleUrls: ['clue-editor.component.scss']
})
export class ClueEditorComponent {
  @Input() cell!: Cell;

  getAllowedDirections(index: number, total: number): ClueDirection[] {
    if (total === 1) {
      return ['RIGHT', 'DOWN', 'FROM_SIDE', 'FROM_BELOW'];
    }
    if (total === 2) {
      if (index === 0) {
        return ['RIGHT', 'FROM_SIDE'];
      }
      if (index === 1) {
        return ['DOWN', 'FROM_BELOW'];
      }
    }
    return [];
  }

  isActiveDirection(clueDirection: ClueDirection, dir: ClueDirection): boolean {
    return clueDirection === dir;
  }
}
