import { Component, inject } from '@angular/core';
import { NotificationsService } from './notifications.service';

@Component({
  selector: 'app-notifications',
  template: `
    <div class="notifications" aria-live="polite" aria-relevant="additions" aria-label="Notifications">
      @for (item of notifications.items(); track item.id) {
        <div class="toast" [class.error]="item.kind === 'error'"
          (mouseenter)="notifications.pause(item.id)" (mouseleave)="notifications.resume(item.id)"
          (focusin)="notifications.pause(item.id)" (focusout)="notifications.resume(item.id)">
          <span class="icon" aria-hidden="true">{{ item.kind === 'success' ? '✓' : item.kind === 'error' ? '!' : 'i' }}</span>
          <div><strong>{{ item.title }}</strong>@if (item.message) { <p>{{ item.message }}</p> }</div>
          <button type="button" aria-label="Dismiss notification" (click)="notifications.dismiss(item.id)">×</button>
        </div>
      }
    </div>`,
  styles: `
    .notifications { position: fixed; top: 1rem; right: 1rem; z-index: 10000; width: min(25rem, calc(100vw - 2rem)); max-height: calc(100dvh - 2rem); overflow-y: auto; display: grid; gap: .75rem; pointer-events: none; }
    .toast { pointer-events: auto; display: flex; align-items: flex-start; gap: .75rem; padding: 1rem; border: 1px solid var(--border); border-left: 4px solid var(--positive); border-radius: .75rem; background: var(--card); color: var(--foreground); box-shadow: 0 8px 30px #0003; font: .9rem/1.5 Arial, sans-serif; overflow-wrap: anywhere; }
    .toast.error { border-left-color: var(--destructive); }
    .icon { color: var(--positive); font-weight: bold; }
    .error .icon { color: var(--destructive); }
    p { margin: .25rem 0 0; }
    button { margin-left: auto; background: transparent; color: inherit; border: 0; cursor: pointer; padding: 0 .4rem; font-size: 1.3rem; }
    button:focus-visible { outline: 2px solid var(--ring); outline-offset: 2px; }
  `,
})
export class NotificationsComponent { readonly notifications = inject(NotificationsService); }
