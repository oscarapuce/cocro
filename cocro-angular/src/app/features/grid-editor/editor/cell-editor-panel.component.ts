import { Component, Input, Output, EventEmitter, OnChanges, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { EditorCell } from './grid-editor.component';
import { ClueDirection } from '../../../shared/models/grid.models';
import { ButtonComponent } from '../../../shared/components/button/button.component';

interface ClueForm { direction: ClueDirection; text: string }

@Component({
  selector: 'app-cell-editor-panel',
  standalone: true,
  imports: [ReactiveFormsModule, ButtonComponent],
  templateUrl: './cell-editor-panel.component.html',
  styleUrl: './cell-editor-panel.component.scss',
})
export class CellEditorPanelComponent implements OnChanges {
  private fb = inject(FormBuilder);

  @Input() cell: EditorCell | null = null;
  @Output() cellUpdated = new EventEmitter<Partial<EditorCell>>();

  form = this.fb.nonNullable.group({
    letter: [''],
    number: [null as number | null],
    clue0direction: ['RIGHT' as ClueDirection],
    clue0text: [''],
    clue1direction: ['DOWN' as ClueDirection],
    clue1text: [''],
  });

  // Available directions per clue slot (CLUE_DOUBLE: slot0=horizontal, slot1=vertical)
  horizontalDirs: ClueDirection[] = ['RIGHT', 'FROM_SIDE'];
  verticalDirs: ClueDirection[] = ['DOWN', 'FROM_BELOW'];
  singleDirs: ClueDirection[] = ['RIGHT', 'DOWN', 'FROM_SIDE', 'FROM_BELOW'];

  dirLabels: Record<ClueDirection, string> = {
    RIGHT: '→ Droite',
    DOWN: '↓ Bas',
    FROM_SIDE: '← Depuis gauche',
    FROM_BELOW: '↑ Depuis bas',
  };

  ngOnChanges(): void {
    if (!this.cell) return;
    this.form.patchValue({
      letter: this.cell.letter ?? '',
      number: this.cell.number ?? null,
      clue0direction: this.cell.clues[0]?.direction ?? 'RIGHT',
      clue0text: this.cell.clues[0]?.text ?? '',
      clue1direction: this.cell.clues[1]?.direction ?? 'DOWN',
      clue1text: this.cell.clues[1]?.text ?? '',
    });
  }

  apply(): void {
    if (!this.cell) return;
    const v = this.form.getRawValue();

    let clues: { direction: ClueDirection; text: string }[] = [];

    if (this.cell.type === 'CLUE_SINGLE') {
      clues = [{ direction: v.clue0direction, text: v.clue0text }];
    } else if (this.cell.type === 'CLUE_DOUBLE') {
      clues = [
        { direction: v.clue0direction, text: v.clue0text },
        { direction: v.clue1direction, text: v.clue1text },
      ];
    }

    this.cellUpdated.emit({
      letter: this.cell.type === 'LETTER' ? v.letter.toUpperCase() : '',
      number: v.number ?? undefined,
      clues,
    });
  }

  get typeLabel(): string {
    const map: Record<string, string> = {
      LETTER: 'Case lettre',
      BLACK: 'Case noire',
      CLUE_SINGLE: 'Définition simple',
      CLUE_DOUBLE: 'Définition double',
    };
    return this.cell ? (map[this.cell.type] ?? '') : '';
  }
}
