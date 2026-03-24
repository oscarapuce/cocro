import { Component, inject, input, output } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '@infrastructure/auth/auth.service';

@Component({
  selector: 'cocro-auth-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './auth-sidebar.component.html',
  styleUrl: './auth-sidebar.component.scss',
})
export class AuthSidebarComponent {
  readonly collapsed = input(false);
  readonly toggle = output<void>();
  readonly navigate = output<void>();

  readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/']);
  }

  onNavigate(): void {
    this.navigate.emit();
  }
}
