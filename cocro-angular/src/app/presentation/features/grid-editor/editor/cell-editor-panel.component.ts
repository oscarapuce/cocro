import { Component, Input, OnChanges, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Cell, ClueDirection } from '@domain/models/grid.model';
import { GridSelectorService } from '@application/services/grid-selector.service';
import { ButtonComponent } from '@presentation/shared/components/button/button.component';

@Component({
  selector: 'app-cell-editor-panel',
  standalone: true,
  imports: [ReactiveFormsModule, ButtonComponent],
  templateUrl: './cell-editor-panel.component.html',
  styleUrl: './cell-editor-panel.component.scss',
})
export class CellEditorPanelComponent implements OnChanges {
  private fb = inject(FormBuilder);
  private selector = inject(GridSelectorService);

  @Input() cell: Cell | null = null;

  form = this.fb.nonNullable.group({
    letter: [''],
    number: [null as number | null],
    clue0direction: ['RIGHT' as ClueDirection],
    clue0text: [''],
    clue1direction: ['DOWN' as ClueDirection],
    clue1text: [''],
  });

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
      letter: this.cell.letter?.value ?? '',
      number: this.cell.letter?.number ?? null,
      clue0direction: this.cell.clues?.[0]?.direction ?? 'RIGHT',
      clue0text: this.cell.clues?.[0]?.text ?? '',
      clue1direction: this.cell.clues?.[1]?.direction ?? 'DOWN',
      clue1text: this.cell.clues?.[1]?.text ?? '',
    });
  }

  apply(): void {
    if (!this.cell) return;
    const v = this.form.getRawValue();

    let updated: Cell = { ...this.cell };

    if (this.cell.type === 'LETTER') {
      updated = {
        ...updated,
        letter: {
          ...(updated.letter ?? { value: '', separator: 'NONE' as const }),
          value: v.letter.toUpperCase(),
          number: v.number ?? undefined,
        },
      };
    } else if (this.cell.type === 'CLUE_SINGLE') {
      updated = {
        ...updated,
        clues: [{ direction: v.clue0direction, text: v.clue0text }],
      };
    } else if (this.cell.type === 'CLUE_DOUBLE') {
      updated = {
        ...updated,
        clues: [
          { direction: v.clue0direction, text: v.clue0text },
          { direction: v.clue1direction, text: v.clue1text },
        ],
      };
    }

    this.selector.updateCellInGrid(updated);
  }

  get typeLabel(): string {
    const map: Record<string, string> = {
      LETTER: 'Case lettre', BLACK: 'Case noire',
      CLUE_SINGLE: 'Définition simple', CLUE_DOUBLE: 'Définition double',
    };
    return this.cell ? (map[this.cell.type] ?? '') : '';
  }
}
