import { Component, input, output, signal } from '@angular/core';
import { ButtonComponent } from '@presentation/shared/components/button/button.component';

@Component({
  selector: 'cocro-play-header',
  standalone: true,
  imports: [ButtonComponent],
  templateUrl: './play-header.component.html',
  styleUrl: './play-header.component.scss',
})
export class PlayHeaderComponent {
  shareCode = input.required<string>();
  title = input.required<string>();
  author = input<string>('');
  difficulty = input<string>('NONE');
  reference = input<string | undefined>(undefined);
  participantCount = input<number>(0);
  revision = input<number>(0);
  connected = input<boolean>(false);

  codeCopied = signal(false);

  leave = output<void>();

  onLeave(): void {
    this.leave.emit();
  }

  copyCode(): void {
    navigator.clipboard.writeText(this.shareCode()).then(() => {
      this.codeCopied.set(true);
      setTimeout(() => this.codeCopied.set(false), 1500);
    });
  }
}
