import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ButtonComponent } from '@presentation/shared/components/button/button.component';

@Component({
  selector: 'app-grid-list',
  standalone: true,
  imports: [RouterLink, ButtonComponent],
  templateUrl: './grid-list.component.html',
  styleUrl: './grid-list.component.scss',
})
export class GridListComponent {}
