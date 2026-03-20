import { Component, inject } from '@angular/core';
import { ToastService } from './toast.service';

@Component({
  selector: 'cocro-toast-container',
  standalone: true,
  templateUrl: './toast.component.html',
  styleUrl: './toast.component.scss',
})
export class ToastContainerComponent {
  readonly toastService = inject(ToastService);
}
