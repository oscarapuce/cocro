import { Component, HostListener, inject, signal, effect } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { GridService } from '@infrastructure/adapters/grid.service';
import { ButtonComponent } from '@presentation/shared/components/button/button.component';
import { InputComponent } from '@presentation/shared/components/input/input.component';
import { CellType, GridDifficulty } from '@domain/models/grid.model';
import { GridSelectorService } from '@application/services/grid-selector.service';
import { createEmptyGrid } from '@domain/services/grid-utils';
import { cellToDto } from '@infrastructure/dto/grid.dto';
import { EditorCellComponent } from './editor-cell.component';
import { CellEditorPanelComponent } from './cell-editor-panel.component';

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
  readonly selector = inject(GridSelectorService);

  metaForm = this.fb.nonNullable.group({
    title: ['', Validators.required],
    difficulty: ['EASY' as GridDifficulty, Validators.required],
    width: [10, [Validators.required, Validators.min(3), Validators.max(20)]],
    height: [10, [Validators.required, Validators.min(3), Validators.max(20)]],
    description: [''],
  });

  activeTool = signal<EditorTool>('SELECT');
  saving = signal(false);
  saveError = signal('');
  saveSuccess = signal(false);

  difficulties: GridDifficulty[] = ['EASY', 'MEDIUM', 'HARD', 'NONE'];

  tools: { value: EditorTool; label: string; title: string }[] = [
    { value: 'SELECT', label: '↖', title: 'Sélectionner' },
    { value: 'LETTER', label: 'A', title: 'Case lettre' },
    { value: 'BLACK', label: '■', title: 'Case noire' },
    { value: 'CLUE_SINGLE', label: '→', title: 'Définition simple' },
    { value: 'CLUE_DOUBLE', label: '↗', title: 'Définition double' },
  ];

  constructor() {
    this.selector.initGrid(createEmptyGrid('0', '', 10, 10));

    effect(() => {
      const w = this.metaForm.controls.width.value;
      const h = this.metaForm.controls.height.value;
      const g = this.selector.grid();
      if (w >= 3 && h >= 3 && (w !== g.width || h !== g.height)) {
        this.selector.onResize(w, h);
      }
    });
  }

  onCellClick(x: number, y: number): void {
    const tool = this.activeTool();
    if (tool === 'SELECT') {
      this.selector.selectOnClick(x, y);
    } else {
      this.selector.selectOnClick(x, y);
      this.selector.onCellTypeChange(tool as CellType);
    }
    // Blur active element so keyboard nav works
    (document.activeElement as HTMLElement)?.blur();
  }

  save(): void {
    if (this.metaForm.invalid) {
      this.metaForm.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.saveError.set('');
    this.saveSuccess.set(false);

    const { title, difficulty, width, height, description } = this.metaForm.getRawValue();
    const cells = this.selector.grid().cells.map(cellToDto);

    this.gridService.submitGrid({ title, difficulty, width, height, description, cells }).subscribe({
      next: () => {
        this.saving.set(false);
        this.saveSuccess.set(true);
      },
      error: () => {
        this.saveError.set('Impossible de sauvegarder la grille.');
        this.saving.set(false);
      },
    });
  }

  @HostListener('window:keydown', ['$event'])
  handleKey(event: KeyboardEvent): void {
    const target = event.target as HTMLElement;
    const tag = target.tagName.toLowerCase();
    if (tag === 'input' || tag === 'textarea' || target.isContentEditable) return;
    if (event.ctrlKey || event.metaKey || event.altKey) return;
    if (['Control', 'Meta', 'Alt', 'AltGraph'].includes(event.key)) return;

    switch (event.key) {
      case 'ArrowRight': this.selector.moveRight(); event.preventDefault(); break;
      case 'ArrowLeft': this.selector.moveLeft(); event.preventDefault(); break;
      case 'ArrowDown': this.selector.moveDown(); event.preventDefault(); break;
      case 'ArrowUp': this.selector.moveUp(); event.preventDefault(); break;
      case 'Backspace': this.selector.handleBackspace(); event.preventDefault(); break;
      case 'Delete': this.selector.handleDelete(); event.preventDefault(); break;
      case 'Shift': this.selector.handleShift(); break;
      default:
        if (/^[a-zA-Z]$/.test(event.key)) {
          this.selector.inputLetter(event.key);
          event.preventDefault();
        }
    }
  }
}
