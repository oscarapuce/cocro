import {NgClass} from '@angular/common';
import {AfterViewInit, Component, ElementRef, Input, OnDestroy, QueryList, ViewChildren} from '@angular/core';

import {Clue} from '@domain/models/grid.model';
import {ClueArrowComponent} from '@presentation/shared/grid/inputs/clues/arrow/clue-arrow.component';
import {KebabCasePipe} from '@presentation/shared/pipes/kebab-case.pipe';

@Component({
  selector: 'cocro-clue-input',
  imports: [
    NgClass,
    ClueArrowComponent,
    KebabCasePipe
  ],
  templateUrl: './clue-input.component.html',
  styleUrl: './clue-input.component.scss'
})
export class ClueInputComponent implements AfterViewInit, OnDestroy {
  private _clues: Clue[] = [];

  @Input()
  set clues(value: Clue[]) {
    this._clues = value;
    this.isMultiClues = value?.length > 1;
    this.observeChanges(); // si clues change dynamiquement
  }

  get clues(): Clue[] {
    return this._clues;
  }

  isMultiClues = false;
  @ViewChildren('clueBlock') clueBlocks!: QueryList<ElementRef>;
  private observers: ResizeObserver[] = [];

  ngAfterViewInit(): void {
    this.observeChanges();
  }

  private observeChanges() {
    this.cleanupObservers();
    this.clueBlocks?.forEach((block: ElementRef) => {
      const observer = new ResizeObserver(() => {
        block.nativeElement.closest('.clue-wrapper')?.classList.toggle(
          'multiple',
          this.isMultiClues
        );
      });
      observer.observe(block.nativeElement);
      this.observers.push(observer);
    });
  }

  private cleanupObservers() {
    this.observers.forEach(o => o.disconnect());
    this.observers = [];
  }

  ngOnDestroy(): void {
    this.cleanupObservers();
  }
}

