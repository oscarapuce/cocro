import { Component, inject, input, output, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '@infrastructure/auth/auth.service';

@Component({
  selector: 'cocro-auth-sidebar',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './auth-sidebar.component.html',
  styleUrl: './auth-sidebar.component.scss',
})
export class AuthSidebarComponent {
  readonly collapsed = input(false);
  readonly toggle = output<void>();
  readonly navigate = output<void>();

  readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly gridMenuOpen = signal(false);
  readonly sessionMenuOpen = signal(false);

  toggleGridMenu(): void {
    this.gridMenuOpen.update(v => !v);
  }

  toggleSessionMenu(): void {
    this.sessionMenuOpen.update(v => !v);
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/']);
  }

  onNavigate(): void {
    this.navigate.emit();
  }
}
