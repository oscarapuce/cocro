import { computed, Injectable, signal } from '@angular/core';
import { Cell, CellType, Direction, Grid } from '@domain/models/grid.model';
import {
  isCellLetter,
  setBlackInCell,
  setDoubleClueInCell,
  setLetterInCell,
  setSingleClueInCell,
  writeLetterInCell,
} from '@domain/services/cell-utils';
import {
  createEmptyGrid,
  getCell,
  getDirectionFromSurroundingClue,
  isOutOfBounds,
  isValidSize,
  resizeGrid,
  withUpdatedCell,
} from '@domain/services/grid-utils';

@Injectable({ providedIn: 'root' })
export class GridSelectorService {
  readonly grid = signal<Grid>(createEmptyGrid('0', '', 10, 10));
  readonly selectedX = signal(0);
  readonly selectedY = signal(0);
  readonly direction = signal<Direction>('NONE');

  readonly selectedCell = computed(() =>
    getCell(this.grid(), this.selectedX(), this.selectedY()),
  );

  readonly rows = computed(() => {
    const g = this.grid();
    const result: Cell[][] = [];
    for (let y = 0; y < g.height; y++) {
      result.push(g.cells.slice(y * g.width, (y + 1) * g.width));
    }
    return result;
  });

  initGrid(grid: Grid): void {
    this.grid.set(grid);
    this.selectedX.set(0);
    this.selectedY.set(0);
    this.direction.set('NONE');
  }

  selectOnClick(x: number, y: number): void {
    const g = this.grid();
    const cell = getCell(g, x, y);
    if (!cell) return;

    const dir = getDirectionFromSurroundingClue(cell, g);
    if (dir !== 'NONE') {
      this.direction.set(dir);
    }

    if (this.selectedX() === x && this.selectedY() === y) {
      this.inverseDirection();
    } else {
      this.select(x, y);
    }
  }

  moveUp(): void { this.move(0, -1); }
  moveDown(): void { this.move(0, 1); }
  moveLeft(): void { this.move(-1, 0); }
  moveRight(): void { this.move(1, 0); }

  inputLetter(letter: string): void {
    const cell = this.selectedCell();
    if (!cell || !isCellLetter(cell)) return;
    const updated = writeLetterInCell(cell, letter);
    this.grid.update(g => withUpdatedCell(g, updated));
    this.goToNextCell();
  }

  handleBackspace(): void {
    this.eraseLetter();
    this.goToNextCell(false);
  }

  handleDelete(): void {
    this.eraseLetter();
  }

  handleShift(): void {
    this.inverseDirection();
  }

  onCellTypeChange(type: CellType): void {
    const cell = this.selectedCell();
    if (!cell) return;

    let updated: Cell;
    switch (type) {
      case 'LETTER': updated = setLetterInCell(cell); break;
      case 'CLUE_SINGLE': updated = setSingleClueInCell(cell); break;
      case 'CLUE_DOUBLE': updated = setDoubleClueInCell(cell); break;
      case 'BLACK': updated = setBlackInCell(cell); break;
    }
    this.grid.update(g => withUpdatedCell(g, updated));
  }

  onResize(newWidth: number, newHeight: number): void {
    if (!isValidSize(newWidth, newHeight)) return;
    this.grid.update(g => resizeGrid(g, newWidth, newHeight));
    if (this.selectedX() >= newWidth) this.selectedX.set(newWidth - 1);
    if (this.selectedY() >= newHeight) this.selectedY.set(newHeight - 1);
  }

  updateCellInGrid(updatedCell: Cell): void {
    this.grid.update(g => withUpdatedCell(g, updatedCell));
  }

  private select(x: number, y: number): void {
    const g = this.grid();
    if (!getCell(g, x, y)) return;
    this.selectedX.set(x);
    this.selectedY.set(y);
  }

  private inverseDirection(): void {
    const d = this.direction();
    if (d === 'DOWNWARDS') this.direction.set('RIGHTWARDS');
    else if (d === 'RIGHTWARDS') this.direction.set('DOWNWARDS');
  }

  private move(dx: number, dy: number): void {
    const g = this.grid();
    const nx = this.selectedX() + dx;
    const ny = this.selectedY() + dy;
    if (isOutOfBounds(nx, ny, g.width, g.height)) return;
    if (!getCell(g, nx, ny)) return;
    this.select(nx, ny);
  }

  private goToNextCell(isGoingForward = true): void {
    const dir = this.direction();
    const g = this.grid();
    if (dir === 'NONE') return;

    const dx = dir === 'RIGHTWARDS' ? (isGoingForward ? 1 : -1) : 0;
    const dy = dir === 'DOWNWARDS' ? (isGoingForward ? 1 : -1) : 0;

    let x = this.selectedX();
    let y = this.selectedY();
    const maxSteps = Math.max(g.width, g.height);
    let steps = 0;

    while (steps++ < maxSteps) {
      const nx = x + dx;
      const ny = y + dy;
      if (isOutOfBounds(nx, ny, g.width, g.height)) break;
      const next = getCell(g, nx, ny);
      if (!next) break;
      if (isCellLetter(next)) {
        this.select(nx, ny);
        break;
      }
      x = nx;
      y = ny;
    }
  }

  private eraseLetter(): void {
    const cell = this.selectedCell();
    if (!cell || !isCellLetter(cell)) return;
    const updated = writeLetterInCell(cell, '');
    this.grid.update(g => withUpdatedCell(g, updated));
  }
}
