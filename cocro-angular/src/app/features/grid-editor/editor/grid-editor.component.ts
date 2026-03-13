import { Component, inject, signal, computed, effect } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { GridService } from '../../../shared/services/grid.service';
import { ButtonComponent } from '../../../shared/components/button/button.component';
import { InputComponent } from '../../../shared/components/input/input.component';
import { CellType, ClueDirection, ClueDto, GridDifficulty } from '../../../shared/models/grid.models';
import { EditorCellComponent } from './editor-cell.component';
import { CellEditorPanelComponent } from './cell-editor-panel.component';

export interface EditorCell {
  x: number;
  y: number;
  type: CellType;
  letter: string;
  number?: number;
  clues: { direction: ClueDirection; text: string }[];
}

export type EditorTool = CellType | 'SELECT';

@Component({
  selector: 'app-grid-editor',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    ButtonComponent,
    InputComponent,
    EditorCellComponent,
    CellEditorPanelComponent,
  ],
  templateUrl: './grid-editor.component.html',
  styleUrl: './grid-editor.component.scss',
})
export class GridEditorComponent {
  private fb = inject(FormBuilder);
  private gridService = inject(GridService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  // — Metadata form —
  metaForm = this.fb.nonNullable.group({
    title: ['', Validators.required],
    difficulty: ['EASY' as GridDifficulty, Validators.required],
    width: [10, [Validators.required, Validators.min(3), Validators.max(20)]],
    height: [10, [Validators.required, Validators.min(3), Validators.max(20)]],
    description: [''],
  });

  // — Grid state —
  gridCells = signal<EditorCell[][]>(this.buildGrid(10, 10));

  // — Selection & tool —
  selectedCell = signal<EditorCell | null>(null);
  activeTool = signal<EditorTool>('SELECT');

  // — Save state —
  saving = signal(false);
  saveError = signal('');
  saveSuccess = signal(false);

  // — Difficulty options —
  difficulties: GridDifficulty[] = ['EASY', 'MEDIUM', 'HARD', 'NONE'];

  // — Tools —
  tools: { value: EditorTool; label: string; title: string }[] = [
    { value: 'SELECT', label: '↖', title: 'Sélectionner' },
    { value: 'LETTER', label: 'A', title: 'Case lettre' },
    { value: 'BLACK', label: '■', title: 'Case noire' },
    { value: 'CLUE_SINGLE', label: '→', title: 'Définition simple' },
    { value: 'CLUE_DOUBLE', label: '↗', title: 'Définition double' },
  ];

  // — Computed rows for template —
  rows = computed(() => this.gridCells());

  constructor() {
    // Rebuild grid when dimensions change
    effect(() => {
      const w = this.metaForm.controls.width.value;
      const h = this.metaForm.controls.height.value;
      if (w >= 3 && h >= 3 && (w !== this.gridCells()[0]?.length || h !== this.gridCells().length)) {
        this.resizeGrid(w, h);
      }
    });
  }

  // — Grid interactions —

  onCellClick(cell: EditorCell): void {
    const tool = this.activeTool();
    if (tool === 'SELECT') {
      this.selectedCell.set(cell);
      return;
    }
    // Apply tool to cell
    this.updateCell(cell.x, cell.y, { type: tool as CellType, clues: [], letter: '' });
    this.selectedCell.set(this.getCell(cell.x, cell.y));
  }

  onCellUpdated(updated: Partial<EditorCell>): void {
    const sel = this.selectedCell();
    if (!sel) return;
    this.updateCell(sel.x, sel.y, updated);
    this.selectedCell.set(this.getCell(sel.x, sel.y));
  }

  // — Save —

  save(): void {
    if (this.metaForm.invalid) {
      this.metaForm.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.saveError.set('');
    this.saveSuccess.set(false);

    const { title, difficulty, width, height, description } = this.metaForm.getRawValue();
    const cells = this.gridCells().flat().map((c) => ({
      x: c.x,
      y: c.y,
      type: c.type,
      letter: c.letter || undefined,
      number: c.number,
      clues: c.clues.length ? c.clues : undefined,
    }));

    this.gridService.submitGrid({ title, difficulty, width, height, description, cells }).subscribe({
      next: (res) => {
        this.saving.set(false);
        this.saveSuccess.set(true);
        // Optionally navigate to create session with this gridId
      },
      error: () => {
        this.saveError.set('Impossible de sauvegarder la grille.');
        this.saving.set(false);
      },
    });
  }

  // — Helpers —

  private buildGrid(width: number, height: number): EditorCell[][] {
    return Array.from({ length: height }, (_, y) =>
      Array.from({ length: width }, (_, x) => ({
        x,
        y,
        type: 'LETTER' as CellType,
        letter: '',
        clues: [],
      })),
    );
  }

  private resizeGrid(width: number, height: number): void {
    const current = this.gridCells();
    const newGrid = this.buildGrid(width, height);
    // Preserve existing cells
    for (let y = 0; y < Math.min(height, current.length); y++) {
      for (let x = 0; x < Math.min(width, current[y]?.length ?? 0); x++) {
        newGrid[y][x] = { ...current[y][x], x, y };
      }
    }
    this.gridCells.set(newGrid);
    this.selectedCell.set(null);
  }

  private updateCell(x: number, y: number, patch: Partial<EditorCell>): void {
    const current = this.gridCells();
    const updated = current.map((row) =>
      row.map((cell) => (cell.x === x && cell.y === y ? { ...cell, ...patch } : cell)),
    );
    this.gridCells.set(updated);
  }

  private getCell(x: number, y: number): EditorCell | null {
    return this.gridCells()[y]?.[x] ?? null;
  }
}
