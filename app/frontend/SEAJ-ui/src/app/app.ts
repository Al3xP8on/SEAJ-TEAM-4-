import { AppearanceService } from './appearance/appearance.service';
import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';

@Component({
  imports: [RouterOutlet],
  selector: 'app-root',
  template: '<router-outlet />',
})
export class App { private readonly appearance = inject(AppearanceService); }
