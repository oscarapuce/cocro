import { Component, inject, OnInit, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { GetMyGridsUseCase } from '@application/use-cases/get-my-grids.use-case';
import { CreateSessionUseCase } from '@application/use-cases/create-session.use-case';
import { DeleteGridUseCase } from '@application/use-cases/delete-grid.use-case';
import { GridSummary } from '@domain/models/grid-summary.model';
import { ToastService } from '@presentation/shared/components/toast/toast.service';
import { getNetworkErrorMessage } from '@infrastructure/http/network-error';
import { GridCardComponent } from './grid-card/grid-card.component';

@Component({
  selector: 'cocro-my-grids',
  standalone: true,
  imports: [GridCardComponent, RouterLink],
  templateUrl: './my-grids.component.html',
  styleUrls: ['./my-grids.component.scss'],
})
export class MyGridsComponent implements OnInit {
  private readonly getMyGrids = inject(GetMyGridsUseCase);
  private readonly createSession = inject(CreateSessionUseCase);
  private readonly deleteGrid = inject(DeleteGridUseCase);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  readonly grids = signal<GridSummary[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly launching = signal<string | null>(null);
  readonly deleting = signal<string | null>(null);

  ngOnInit(): void {
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

  onDelete(grid: GridSummary): void {
    if (this.deleting()) return;
    if (!confirm(`Supprimer la grille « ${grid.title} » ? Cette action est irréversible.`)) return;
    this.deleting.set(grid.gridId);
    this.deleteGrid.execute(grid.gridId).subscribe({
      next: () => {
        this.grids.update(list => list.filter(g => g.gridId !== grid.gridId));
        this.toast.success('Grille supprimée.');
        this.deleting.set(null);
      },
      error: (err: unknown) => {
        this.toast.error(getNetworkErrorMessage(err, 'Impossible de supprimer la grille.'));
        this.deleting.set(null);
      },
    });
  }

  retry(): void {
    this.loadGrids();
  }
}
