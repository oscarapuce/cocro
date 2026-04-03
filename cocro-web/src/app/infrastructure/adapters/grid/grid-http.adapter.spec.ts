import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { GridHttpAdapter } from './grid-http.adapter';
import { Grid } from '@domain/models/grid.model';
import { GridSummary } from '@domain/models/grid-summary.model';
import { SubmitGridRequest, PatchGridRequest, GridSubmitResponse } from '@application/dto/grid.dto';

const BASE_URL = 'http://localhost:8080/api/grids';

const GRID_STUB: Grid = {
  id: 'grid-1',
  title: 'Test Grid',
  width: 10,
  height: 8,
  cells: [],
  difficulty: '2',
};

const GRID_SUMMARY_STUB: GridSummary = {
  gridId: 'grid-1',
  title: 'Test Grid',
  width: 10,
  height: 8,
  difficulty: '2',
  createdAt: '2026-03-20T10:00:00Z',
  updatedAt: '2026-03-20T12:00:00Z',
};

const SUBMIT_REQUEST: SubmitGridRequest = {
  title: 'New Grid',
  difficulty: '1',
  width: 10,
  height: 8,
  cells: [],
};

const SUBMIT_RESPONSE: GridSubmitResponse = {
  gridId: 'grid-new-1',
};

const PATCH_REQUEST: PatchGridRequest = {
  gridId: 'grid-1',
  title: 'Updated Title',
};

describe('GridHttpAdapter', () => {
  let adapter: GridHttpAdapter;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        GridHttpAdapter,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });

    adapter = TestBed.inject(GridHttpAdapter);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  describe('getGrid', () => {
    it('should GET /api/grids/{gridId}', () => {
      adapter.getGrid('grid-1').subscribe();

      const req = httpMock.expectOne(`${BASE_URL}/grid-1`);
      expect(req.request.method).toBe('GET');
      req.flush(GRID_STUB);
    });

    it('should use the provided gridId in the URL', () => {
      adapter.getGrid('different-id').subscribe();

      const req = httpMock.expectOne(`${BASE_URL}/different-id`);
      expect(req.request.method).toBe('GET');
      req.flush(GRID_STUB);
    });

    it('should return the Grid', (done) => {
      adapter.getGrid('grid-1').subscribe((result) => {
        expect(result).toEqual(GRID_STUB);
        done();
      });

      httpMock.expectOne(`${BASE_URL}/grid-1`).flush(GRID_STUB);
    });
  });

  describe('getMyGrids', () => {
    it('should GET /api/grids/mine', () => {
      adapter.getMyGrids().subscribe();

      const req = httpMock.expectOne(`${BASE_URL}/mine`);
      expect(req.request.method).toBe('GET');
      req.flush([GRID_SUMMARY_STUB]);
    });

    it('should return an array of GridSummary', (done) => {
      adapter.getMyGrids().subscribe((result) => {
        expect(result).toEqual([GRID_SUMMARY_STUB]);
        done();
      });

      httpMock.expectOne(`${BASE_URL}/mine`).flush([GRID_SUMMARY_STUB]);
    });

    it('should return an empty array when no grids exist', (done) => {
      adapter.getMyGrids().subscribe((result) => {
        expect(result).toEqual([]);
        done();
      });

      httpMock.expectOne(`${BASE_URL}/mine`).flush([]);
    });
  });

  describe('submitGrid', () => {
    it('should POST to /api/grids with the request body', () => {
      adapter.submitGrid(SUBMIT_REQUEST).subscribe();

      const req = httpMock.expectOne(BASE_URL);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(SUBMIT_REQUEST);
      req.flush(SUBMIT_RESPONSE);
    });

    it('should return the GridSubmitResponse', (done) => {
      adapter.submitGrid(SUBMIT_REQUEST).subscribe((result) => {
        expect(result).toEqual(SUBMIT_RESPONSE);
        done();
      });

      httpMock.expectOne(BASE_URL).flush(SUBMIT_RESPONSE);
    });
  });

  describe('patchGrid', () => {
    it('should PATCH /api/grids with the request body', () => {
      adapter.patchGrid(PATCH_REQUEST).subscribe();

      const req = httpMock.expectOne(BASE_URL);
      expect(req.request.method).toBe('PATCH');
      expect(req.request.body).toEqual(PATCH_REQUEST);
      req.flush(null);
    });

    it('should include the gridId in the request body', () => {
      adapter.patchGrid(PATCH_REQUEST).subscribe();

      const req = httpMock.expectOne(BASE_URL);
      expect(req.request.body.gridId).toBe('grid-1');
      req.flush(null);
    });

    it('should complete without emitting a value on success', (done) => {
      adapter.patchGrid(PATCH_REQUEST).subscribe({
        complete: () => {
          done();
        },
      });

      httpMock.expectOne(BASE_URL).flush(null);
    });
  });

  describe('deleteGrid', () => {
    it('should DELETE /api/grids/{gridId}', () => {
      adapter.deleteGrid('grid-1').subscribe();

      const req = httpMock.expectOne(`${BASE_URL}/grid-1`);
      expect(req.request.method).toBe('DELETE');
      req.flush(null);
    });

    it('should use the provided gridId in the URL', () => {
      adapter.deleteGrid('another-grid').subscribe();

      const req = httpMock.expectOne(`${BASE_URL}/another-grid`);
      expect(req.request.method).toBe('DELETE');
      req.flush(null);
    });

    it('should complete without emitting a value on success', (done) => {
      adapter.deleteGrid('grid-1').subscribe({
        complete: () => {
          done();
        },
      });

      httpMock.expectOne(`${BASE_URL}/grid-1`).flush(null);
    });
  });
});
