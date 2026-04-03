import { Component, computed, ElementRef, HostListener, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AUTH_PORT, AuthPort } from '@application/ports/auth/auth.port';

@Component({
  selector: 'cocro-user-profile-widget',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './user-profile-widget.component.html',
  styleUrl: './user-profile-widget.component.scss',
})
export class UserProfileWidgetComponent {
  readonly auth = inject<AuthPort>(AUTH_PORT);
  private readonly router = inject(Router);
  private readonly el = inject(ElementRef);

  readonly dropdownOpen = signal(false);

  readonly avatarLetter = computed(() =>
    (this.auth.currentUser()?.username?.[0] ?? '?').toUpperCase()
  );

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.el.nativeElement.contains(event.target)) {
      this.dropdownOpen.set(false);
    }
  }

  toggleDropdown(): void {
    this.dropdownOpen.update((v) => !v);
  }

  logout(): void {
    this.dropdownOpen.set(false);
    this.auth.logout();
    this.router.navigate(['/']);
  }
}
