import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'cocro-card',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="cocro-card" [class.cocro-card--dark]="dark">
      <ng-content />
    </div>
  `,
  styleUrl: './card.component.scss',
})
export class CardComponent {
  /** Dark variant: forest-green background, white text */
  @Input() dark = false;
}
