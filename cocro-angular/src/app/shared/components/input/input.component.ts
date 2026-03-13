import { Component, Input, forwardRef } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR, ReactiveFormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'cocro-input',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => InputComponent),
      multi: true,
    },
  ],
  template: `
    <div class="cocro-field">
      <label *ngIf="label" [for]="inputId" class="cocro-field__label">{{ label }}</label>
      <input
        [id]="inputId"
        [type]="type"
        [placeholder]="placeholder"
        [disabled]="isDisabled"
        [value]="value"
        (input)="onInput($event)"
        (blur)="onTouched()"
        class="cocro-field__input"
        [class.cocro-field__input--error]="hasError"
      />
      <span *ngIf="hasError && errorMessage" class="cocro-field__error">{{ errorMessage }}</span>
    </div>
  `,
  styleUrl: './input.component.scss',
})
export class InputComponent implements ControlValueAccessor {
  @Input() label = '';
  @Input() placeholder = '';
  @Input() type: 'text' | 'password' | 'email' = 'text';
  @Input() hasError = false;
  @Input() errorMessage = '';
  @Input() inputId = `cocro-input-${Math.random().toString(36).slice(2, 7)}`;

  value = '';
  isDisabled = false;

  onChange: (v: string) => void = () => {};
  onTouched: () => void = () => {};

  onInput(event: Event): void {
    this.value = (event.target as HTMLInputElement).value;
    this.onChange(this.value);
  }

  writeValue(v: string): void { this.value = v ?? ''; }
  registerOnChange(fn: (v: string) => void): void { this.onChange = fn; }
  registerOnTouched(fn: () => void): void { this.onTouched = fn; }
  setDisabledState(disabled: boolean): void { this.isDisabled = disabled; }
}
