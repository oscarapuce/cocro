import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'cocro-card',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="cocro-card" [class.cocro-card--dark]="dark" [class.cocro-card--labeled]="!!title">
      @if (title) {
        <span class="cocro-card__label">{{ title }}</span>
      }
      <ng-content />
    </div>
  `,
  styleUrl: './card.component.scss',
})
export class CardComponent {
  /** Dark variant: forest-green background, white text */
  @Input() dark = false;
  /** Label displayed on the top border (fieldset legend style) */
  @Input() title = '';
}
