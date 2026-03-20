import { Component, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '@infrastructure/auth/auth.service';
import { ButtonComponent } from '@presentation/shared/components/button/button.component';

@Component({
  selector: 'cocro-header',
  standalone: true,
  imports: [RouterLink, ButtonComponent],
  templateUrl: './header.component.html',
  styleUrl: './header.component.scss',
})
export class HeaderComponent {
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/']);
  }
}
