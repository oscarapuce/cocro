import { Component, input } from '@angular/core';

@Component({
  selector: 'cocro-landing-home-shell',
  standalone: true,
  templateUrl: './landing-home-shell.component.html',
  styleUrl: './landing-home-shell.component.scss',
})
export class LandingHomeShellComponent {
  readonly showTopNav = input(true);
  readonly navUser = input('');
  readonly heroTitle = input('');
  readonly heroSubtitle = input('');
}
