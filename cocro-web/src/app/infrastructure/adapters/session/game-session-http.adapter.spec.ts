import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { GameSessionHttpAdapter } from './game-session-http.adapter';
import {
  CreateSessionRequest,
  JoinSessionRequest,
  LeaveSessionRequest,
  SessionCreatedResponse,
  SessionFullResponse,
  SessionLeaveResponse,
  SessionStateResponse,
  GridCheckResponse,
} from '@domain/models/session.model';

const BASE_URL = 'http://localhost:8080/api/sessions';

const SESSION_CREATED: SessionCreatedResponse = {
  sessionId: 'sess-1',
  shareCode: 'ABC123',
};

const SESSION_FULL: SessionFullResponse = {
  sessionId: 'sess-1',
  shareCode: 'ABC123',
  status: 'PLAYING',
  participantCount: 1,
  participants: [],
  topicToSubscribe: '/topic/session/ABC123',
  gridTemplate: {} as any,
  gridRevision: 0,
  cells: [],
};

const SESSION_LEAVE: SessionLeaveResponse = {
  sessionId: 'sess-1',
};

const SESSION_STATE: SessionStateResponse = {
  sessionId: 'sess-1',
  shareCode: 'ABC123',
  revision: 3,
  cells: [],
};

const GRID_CHECK: GridCheckResponse = {
  shareCode: 'ABC123',
  isComplete: false,
  isCorrect: false,
  correctCount: 2,
  totalCount: 10,
  filledCount: 4,
  wrongCount: 2,
};

describe('GameSessionHttpAdapter', () => {
  let adapter: GameSessionHttpAdapter;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        GameSessionHttpAdapter,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });

    adapter = TestBed.inject(GameSessionHttpAdapter);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  describe('createSession', () => {
    const request: CreateSessionRequest = { gridId: 'grid-42' };

    it('should POST to /api/sessions with the request body', () => {
      adapter.createSession(request).subscribe();

      const req = httpMock.expectOne(BASE_URL);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(request);
      req.flush(SESSION_CREATED);
    });

    it('should return the SessionCreatedResponse', (done) => {
      adapter.createSession(request).subscribe((result) => {
        expect(result).toEqual(SESSION_CREATED);
        done();
      });

      httpMock.expectOne(BASE_URL).flush(SESSION_CREATED);
    });
  });

  describe('joinSession', () => {
    const request: JoinSessionRequest = { shareCode: 'ABC123' };

    it('should POST to /api/sessions/join with the request body', () => {
      adapter.joinSession(request).subscribe();

      const req = httpMock.expectOne(`${BASE_URL}/join`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(request);
      req.flush(SESSION_FULL);
    });

    it('should return the SessionFullResponse', (done) => {
      adapter.joinSession(request).subscribe((result) => {
        expect(result).toEqual(SESSION_FULL);
        done();
      });

      httpMock.expectOne(`${BASE_URL}/join`).flush(SESSION_FULL);
    });
  });

  describe('leaveSession', () => {
    const request: LeaveSessionRequest = { shareCode: 'ABC123' };

    it('should POST to /api/sessions/leave with the request body', () => {
      adapter.leaveSession(request).subscribe();

      const req = httpMock.expectOne(`${BASE_URL}/leave`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(request);
      req.flush(SESSION_LEAVE);
    });

    it('should return the SessionLeaveResponse', (done) => {
      adapter.leaveSession(request).subscribe((result) => {
        expect(result).toEqual(SESSION_LEAVE);
        done();
      });

      httpMock.expectOne(`${BASE_URL}/leave`).flush(SESSION_LEAVE);
    });
  });

  describe('getState', () => {
    it('should GET /api/sessions/{shareCode}/state', () => {
      adapter.getState('ABC123').subscribe();

      const req = httpMock.expectOne(`${BASE_URL}/ABC123/state`);
      expect(req.request.method).toBe('GET');
      req.flush(SESSION_STATE);
    });

    it('should return the SessionStateResponse', (done) => {
      adapter.getState('ABC123').subscribe((result) => {
        expect(result).toEqual(SESSION_STATE);
        done();
      });

      httpMock.expectOne(`${BASE_URL}/ABC123/state`).flush(SESSION_STATE);
    });
  });

  describe('syncSession', () => {
    it('should POST to /api/sessions/{shareCode}/sync with an empty body', () => {
      adapter.syncSession('ABC123').subscribe();

      const req = httpMock.expectOne(`${BASE_URL}/ABC123/sync`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({});
      req.flush(SESSION_FULL);
    });

    it('should return the SessionFullResponse', (done) => {
      adapter.syncSession('ABC123').subscribe((result) => {
        expect(result).toEqual(SESSION_FULL);
        done();
      });

      httpMock.expectOne(`${BASE_URL}/ABC123/sync`).flush(SESSION_FULL);
    });
  });

  describe('checkGrid', () => {
    it('should POST to /api/sessions/{shareCode}/check with an empty body', () => {
      adapter.checkGrid('ABC123').subscribe();

      const req = httpMock.expectOne(`${BASE_URL}/ABC123/check`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({});
      req.flush(GRID_CHECK);
    });

    it('should return the GridCheckResponse', (done) => {
      adapter.checkGrid('ABC123').subscribe((result) => {
        expect(result).toEqual(GRID_CHECK);
        done();
      });

      httpMock.expectOne(`${BASE_URL}/ABC123/check`).flush(GRID_CHECK);
    });
  });
});
