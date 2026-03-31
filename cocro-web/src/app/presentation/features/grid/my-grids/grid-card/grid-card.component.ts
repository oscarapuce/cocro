import {
  Component, Input, Output, EventEmitter,
  signal, inject, computed, HostListener,
} from '@angular/core';
import { GridSummary } from '@domain/models/grid-summary.model';
import { Grid } from '@domain/models/grid.model';
import { DatePipe } from '@angular/common';
import { GRID_PORT } from '@application/ports/grid/grid.port';

const TOOLTIP_W = 216;
const TOOLTIP_H = 240; // hauteur max estimée
const OFFSET    = 20;  // distance entre curseur et tooltip

@Component({
  selector: 'cocro-grid-card',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './grid-card.component.html',
  styleUrls: ['./grid-card.component.scss'],
})
export class GridCardComponent {
  @Input({ required: true }) grid!: GridSummary;
  @Input() launching = false;
  @Output() launchSession = new EventEmitter<void>();
  @Output() edit = new EventEmitter<void>();

  private readonly gridPort = inject(GRID_PORT);

  readonly detail        = signal<Grid | null>(null);
  readonly tooltipActive = signal(false);

  private readonly _mouseX = signal(0);
  private readonly _mouseY = signal(0);
  private fetched = false;

  /** Style position: fixed calculé à chaque mouvement de souris */
  readonly tooltipStyle = computed(() => {
    const x  = this._mouseX();
    const y  = this._mouseY();
    const vw = typeof window !== 'undefined' ? window.innerWidth  : 1440;
    const vh = typeof window !== 'undefined' ? window.innerHeight : 900;

    // Flip horizontal si trop près du bord droit
    // Flip horizontal : trop proche du bord droit → tooltip à gauche du curseur
    const flipX = x + OFFSET + TOOLTIP_W > vw;
    // Flip vertical : trop proche du bord bas → tooltip au-dessus du curseur
    const flipY = y + OFFSET + TOOLTIP_H > vh;

    return {
      left: flipX ? (x - OFFSET - TOOLTIP_W) + 'px' : (x + OFFSET) + 'px',
      top:  flipY ? (y - OFFSET - TOOLTIP_H) + 'px'  : (y + OFFSET) + 'px',
    };
  });

  @HostListener('mousemove', ['$event'])
  onMouseMove(e: MouseEvent): void {
    if (this.tooltipActive()) {
      this._mouseX.set(e.clientX);
      this._mouseY.set(e.clientY);
    }
  }

  onMouseEnter(e: MouseEvent): void {
    this._mouseX.set(e.clientX);
    this._mouseY.set(e.clientY);
    this.tooltipActive.set(true);
    if (!this.fetched) {
      this.fetched = true;
      this.gridPort.getGrid(this.grid.gridId).subscribe({
        next: g => this.detail.set(g),
      });
    }
  }

  onMouseLeave(): void {
    this.tooltipActive.set(false);
  }

  get difficultyLabel(): string {
    const d = this.grid.difficulty;
    if (!d || d === 'NONE') return '—';
    const level = parseInt(d.split('-')[0], 10);
    if (isNaN(level)) return d;
    const filled = Math.min(level + 1, 5);
    return '★'.repeat(filled) + '☆'.repeat(5 - filled);
  }

  get miniatureWidth(): number {
    const max = 160;
    const { width, height } = this.grid;
    return width >= height ? max : Math.round(max * width / height);
  }

  get miniatureHeight(): number {
    const max = 160;
    const { width, height } = this.grid;
    return height >= width ? max : Math.round(max * height / width);
  }

  cellFill(type: string): string {
    if (type === 'BLACK')                              return '#5a6057';
    if (type === 'CLUE_SINGLE' || type === 'CLUE_DOUBLE') return '#c0aa88';
    return '#f7f4ed';
  }
}
