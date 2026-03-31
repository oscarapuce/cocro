import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class LetterAuthorService {
  private readonly _authors = signal(new Map<string, string>());

  readonly authors = this._authors.asReadonly();

  getAuthor(x: number, y: number): string | undefined {
    return this._authors().get(`${x},${y}`);
  }

  setAuthor(x: number, y: number, authorId: string): void {
    this._authors.update(m => {
      const next = new Map(m);
      next.set(`${x},${y}`, authorId);
      return next;
    });
  }

  clearAuthor(x: number, y: number): void {
    this._authors.update(m => {
      const next = new Map(m);
      next.delete(`${x},${y}`);
      return next;
    });
  }

  clearAll(): void {
    this._authors.set(new Map());
  }
}
