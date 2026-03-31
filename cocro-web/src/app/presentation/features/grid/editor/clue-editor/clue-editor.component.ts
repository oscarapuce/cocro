import { Component, Input } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Cell, ClueDirection } from '@domain/models/grid.model';
import { getAllowedClueDirections } from '@domain/services/cell-utils.service';
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
    return getAllowedClueDirections(index, total);
  }

  isActiveDirection(clueDirection: ClueDirection, dir: ClueDirection): boolean {
    return clueDirection === dir;
  }
}
