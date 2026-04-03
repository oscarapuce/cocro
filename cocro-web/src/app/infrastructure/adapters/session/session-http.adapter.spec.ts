import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { SessionHttpAdapter } from './session-http.adapter';
import { SessionSummary } from '@domain/models/session-summary.model';

const BASE_URL = 'http://localhost:8080/api/sessions';

const SESSION_SUMMARY_STUB: SessionSummary = {
  sessionId: 'sess-10',
  shareCode: 'CODE01',
  status: 'PLAYING',
  gridTitle: 'My Grid',
  gridDimension: { width: 10, height: 8 },
  authorName: 'alice',
  participantCount: 2,
  role: 'CREATOR',
  createdAt: '2026-04-01T10:00:00Z',
  updatedAt: '2026-04-01T11:00:00Z',
};

describe('SessionHttpAdapter', () => {
  let adapter: SessionHttpAdapter;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        SessionHttpAdapter,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });

    adapter = TestBed.inject(SessionHttpAdapter);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  describe('getMySessions', () => {
    it('should GET /api/sessions/mine', () => {
      adapter.getMySessions().subscribe();

      const req = httpMock.expectOne(`${BASE_URL}/mine`);
      expect(req.request.method).toBe('GET');
      req.flush([SESSION_SUMMARY_STUB]);
    });

    it('should return an array of SessionSummary', (done) => {
      adapter.getMySessions().subscribe((result) => {
        expect(result).toEqual([SESSION_SUMMARY_STUB]);
        expect(result).toHaveLength(1);
        done();
      });

      httpMock.expectOne(`${BASE_URL}/mine`).flush([SESSION_SUMMARY_STUB]);
    });

    it('should return an empty array when no sessions exist', (done) => {
      adapter.getMySessions().subscribe((result) => {
        expect(result).toEqual([]);
        done();
      });

      httpMock.expectOne(`${BASE_URL}/mine`).flush([]);
    });
  });

  describe('deleteSession', () => {
    it('should DELETE /api/sessions/{shareCode}', () => {
      adapter.deleteSession('CODE01').subscribe();

      const req = httpMock.expectOne(`${BASE_URL}/CODE01`);
      expect(req.request.method).toBe('DELETE');
      req.flush(null);
    });

    it('should use the provided shareCode in the URL', () => {
      adapter.deleteSession('DIFFERENT-CODE').subscribe();

      const req = httpMock.expectOne(`${BASE_URL}/DIFFERENT-CODE`);
      expect(req.request.method).toBe('DELETE');
      req.flush(null);
    });

    it('should complete without emitting a value on success', (done) => {
      adapter.deleteSession('CODE01').subscribe({
        complete: () => {
          done();
        },
      });

      httpMock.expectOne(`${BASE_URL}/CODE01`).flush(null);
    });
  });
});
