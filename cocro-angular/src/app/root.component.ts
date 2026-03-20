import { Component, inject, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { Router, NavigationEnd } from '@angular/router';
import { HeaderComponent } from '@presentation/shared/header/header.component';
import { ToastContainerComponent } from '@presentation/shared/components/toast/toast.component';
import { AuthService } from '@infrastructure/auth/auth.service';
import { AuthSidebarComponent } from '@presentation/shared/sidebar/auth-sidebar.component';
import { filter } from 'rxjs';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, HeaderComponent, ToastContainerComponent, AuthSidebarComponent],
  templateUrl: './root.component.html',
  styleUrl: './root.component.scss',
})
export class RootComponent {
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly currentUrl = signal(this.router.url);
  readonly sidebarCollapsed = signal(false);

  constructor() {
    this.router.events
      .pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd))
      .subscribe((event) => {
        this.currentUrl.set(event.urlAfterRedirects);
      });
  }

  readonly hasToolSidebar = (): boolean =>
    this.currentUrl() === '/' ||
    (this.auth.isAuthenticated() &&
      (/^\/home(\/|$)|^\/grid(\/|$)|^\/lobby(\/|$)|^\/game(\/|$)/).test(this.currentUrl()));

  readonly hasTopHeader = (): boolean =>
    this.auth.isAuthenticated() && !this.hasToolSidebar();

  toggleSidebar(): void {
    this.sidebarCollapsed.update((value) => !value);
  }

  collapseSidebar(): void {
    this.sidebarCollapsed.set(true);
  }

  collapseSidebarFromContent(): void {
    if (!this.sidebarCollapsed()) {
      this.collapseSidebar();
    }
  }
}
