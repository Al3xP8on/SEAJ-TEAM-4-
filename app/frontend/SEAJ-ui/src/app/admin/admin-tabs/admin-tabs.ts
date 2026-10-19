import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

/**
 * Switches between the admin pages. These are links to separate pages, styled as tabs, so they're
 * a nav with aria-current rather than ARIA tabs (which switch panels within one page).
 */
@Component({
  selector: 'app-admin-tabs',
  imports: [RouterLink, RouterLinkActive],
  template: `
    <nav class="admin-tabs" aria-label="Administration">
      <a
        routerLink="/admin"
        routerLinkActive="active"
        [routerLinkActiveOptions]="{ exact: true }"
        ariaCurrentWhenActive="page"
        >Accounts</a
      >
      <a routerLink="/admin/compliance" routerLinkActive="active" ariaCurrentWhenActive="page"
        >Compliance</a
      >
    </nav>
  `,
  styles: `
    .admin-tabs {
      display: flex;
      gap: 4px;
      margin-bottom: 24px;
      border-bottom: 1px solid var(--border);
    }
    a {
      padding: 10px 16px;
      margin-bottom: -1px;
      border-bottom: 3px solid transparent;
      color: var(--muted-foreground);
      font-weight: 500;
      text-decoration: none;
    }
    a:hover {
      color: var(--foreground);
    }
    /* The current page is marked by weight and an underline, not colour alone */
    a.active {
      color: var(--foreground);
      font-weight: 600;
      border-bottom-color: var(--positive);
    }
    a:focus-visible {
      outline: 3px solid var(--focus);
      outline-offset: 2px;
    }
  `,
})
export class AdminTabsComponent {}
