import { Inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { GridPort, GRID_PORT } from '@application/ports/grid/grid.port';
import { SubmitGridRequest } from '@application/dto/grid.dto';

@Injectable({ providedIn: 'root' })
export class CreateGridUseCase {
  constructor(@Inject(GRID_PORT) private gridPort: GridPort) {}

  async execute(request: SubmitGridRequest): Promise<string> {
    const response = await firstValueFrom(this.gridPort.submitGrid(request));
    return response.gridId;
  }
}
