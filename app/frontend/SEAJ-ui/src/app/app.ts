import { AppearanceService } from './appearance/appearance.service';
import { Component, inject } from '@angular/core';
import { NotificationsComponent } from './notifications/notifications';
import { RouterOutlet } from '@angular/router';

@Component({
  imports: [RouterOutlet, NotificationsComponent],
  selector: 'app-root',
  template: '<router-outlet /><app-notifications />',
})
export class App { private readonly appearance = inject(AppearanceService); }
