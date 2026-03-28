import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { GetMyGridsUseCase } from '@application/use-cases/get-my-grids.use-case';
import { CreateSessionUseCase } from '@application/use-cases/create-session.use-case';
import { GridSummary } from '@domain/models/grid-summary.model';
import { ToastService } from '@presentation/shared/components/toast/toast.service';
import { GridCardComponent } from './grid-card/grid-card.component';
import { ButtonComponent } from '@presentation/shared/components/button/button.component';

@Component({
  selector: 'cocro-my-grids',
  standalone: true,
  imports: [GridCardComponent, ButtonComponent, RouterLink],
  templateUrl: './my-grids.component.html',
  styleUrls: ['./my-grids.component.scss'],
})
export class MyGridsComponent {
  private readonly getMyGrids = inject(GetMyGridsUseCase);
  private readonly createSession = inject(CreateSessionUseCase);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  readonly grids = signal<GridSummary[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly launching = signal<string | null>(null);

  constructor() {
    this.loadGrids();
  }

  private loadGrids(): void {
    this.loading.set(true);
    this.error.set('');
    this.getMyGrids.execute().subscribe({
      next: (grids) => {
        this.grids.set(grids);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger vos grilles.');
        this.loading.set(false);
      },
    });
  }

  onLaunch(grid: GridSummary): void {
    if (this.launching()) return;
    this.launching.set(grid.gridId);
    this.createSession.execute(grid.gridId).subscribe({
      next: (session) => {
        this.router.navigate(['/play', session.shareCode]);
      },
      error: () => {
        this.toast.error('Impossible de lancer la session.');
        this.launching.set(null);
      },
    });
  }

  onEdit(grid: GridSummary): void {
    this.router.navigate(['/grid', grid.gridId, 'edit']);
  }

  retry(): void {
    this.loadGrids();
  }
}
