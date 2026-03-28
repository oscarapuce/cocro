import { TestBed } from '@angular/core/testing';
import { LetterAuthorService } from './letter-author.service';

describe('LetterAuthorService', () => {
  let service: LetterAuthorService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [LetterAuthorService],
    });
    service = TestBed.inject(LetterAuthorService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should return undefined for unknown coordinates', () => {
    expect(service.getAuthor(0, 0)).toBeUndefined();
  });

  it('should set and get an author', () => {
    service.setAuthor(3, 5, 'user-42');
    expect(service.getAuthor(3, 5)).toBe('user-42');
  });

  it('should overwrite an existing author', () => {
    service.setAuthor(1, 2, 'alice');
    service.setAuthor(1, 2, 'bob');
    expect(service.getAuthor(1, 2)).toBe('bob');
  });

  it('should clear a single author', () => {
    service.setAuthor(4, 7, 'user-1');
    service.clearAuthor(4, 7);
    expect(service.getAuthor(4, 7)).toBeUndefined();
  });

  it('should clear all authors', () => {
    service.setAuthor(0, 0, 'a');
    service.setAuthor(1, 1, 'b');
    service.clearAll();
    expect(service.getAuthor(0, 0)).toBeUndefined();
    expect(service.getAuthor(1, 1)).toBeUndefined();
  });

  it('should expose authors as a readable signal', () => {
    service.setAuthor(2, 3, 'me');
    const authors = service.authors();
    expect(authors.get('2,3')).toBe('me');
  });
});
