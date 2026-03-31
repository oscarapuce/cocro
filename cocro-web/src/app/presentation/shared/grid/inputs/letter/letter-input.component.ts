import {Component, EventEmitter, Input, Output} from '@angular/core';
import {NgClass} from '@angular/common';
import {Letter} from '@domain/models/grid.model';
import {DEFAULT_LETTER} from '@domain/services/cell-utils.service';

@Component({
  selector: 'cocro-letter-input',
  standalone: true,
  imports: [NgClass],
  templateUrl: './letter-input.component.html',
  styleUrls: ['./letter-input.component.scss']
})
export class LetterInputComponent {
  private _letter: Letter = { ...DEFAULT_LETTER };

  @Input() set letter(value: Letter | undefined) {
    this._letter = value ?? { ...DEFAULT_LETTER };
  }
  get letter(): Letter {
    return this._letter;
  }
  @Input() active: boolean = false;
  @Input() colorClass = '';
  @Output() valueChange = new EventEmitter<string>();

  onInput(event: Event): void {
    const input = (event.target as HTMLInputElement).value.toUpperCase();
    if (/^[A-Z]?$/.test(input)) {
      this.valueChange.emit(input);
    } else {
      // Si caractère invalide, on efface
      (event.target as HTMLInputElement).value = '';
    }
  }
}
