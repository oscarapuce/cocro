import { Component, Input } from '@angular/core';
import { KebabCasePipe } from '@presentation/shared/pipes/kebab-case.pipe';
import { ClueDirection } from '@domain/models/grid.model';
import { NgClass } from '@angular/common';

const STRAIGHT_ARROW_SVG_PATH = 'M10 3h4v10h5l-7 8-7-8h5z';
const ELBOWED_ARROW_SVG_PATH = 'M-8 6h28v8h4l-6 6-6-6h4V10H6V6z';

@Component({
  selector: 'cocro-clue-arrow',
  standalone: true,
  imports: [
    KebabCasePipe,
    NgClass,
  ],
  templateUrl: './clue-arrow.component.html',
  styleUrl: './clue-arrow.component.scss'
})
export class ClueArrowComponent {

  @Input() direction: ClueDirection = 'DOWN';
  @Input() inline = false;

  getArrowSvgPath(): string {
    switch (this.direction) {
      case 'DOWN':
      case 'RIGHT':
        return STRAIGHT_ARROW_SVG_PATH;
      case 'FROM_BELOW':
      case 'FROM_SIDE':
        return ELBOWED_ARROW_SVG_PATH;
      default:
        console.warn(`Unknown direction: ${this.direction}`);
        return STRAIGHT_ARROW_SVG_PATH;
    }
  }
}
