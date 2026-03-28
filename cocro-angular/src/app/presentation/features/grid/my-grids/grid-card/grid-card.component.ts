import { Component, EventEmitter, Input, Output } from '@angular/core';
import { GridSummary } from '@domain/models/grid-summary.model';
import { ButtonComponent } from '@presentation/shared/components/button/button.component';
import { DatePipe } from '@angular/common';

@Component({
  selector: 'cocro-grid-card',
  standalone: true,
  imports: [ButtonComponent, DatePipe],
  templateUrl: './grid-card.component.html',
  styleUrls: ['./grid-card.component.scss'],
})
export class GridCardComponent {
  @Input({ required: true }) grid!: GridSummary;
  @Output() launchSession = new EventEmitter<void>();
  @Output() edit = new EventEmitter<void>();
}
