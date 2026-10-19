// Lets protected routes open only with a live session; otherwise sends the user to login.
import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = () =>
  inject(AuthService).isAuthenticated() || inject(Router).createUrlTree(['/login']);

// Navigation hints only; the API independently enforces admin permissions.
export function hasAdminRole(token: string | null): boolean {
  try {
    const payload = token?.split('.')[1];
    if (!payload) return false;
    const roles = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/'))).roles;
    return Array.isArray(roles) && roles.includes('ADMIN');
  } catch {
    return false;
  }
}

export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.isAuthenticated()) return router.createUrlTree(['/login']);
  return hasAdminRole(auth.getToken()) || router.createUrlTree(['/dashboard']);
};

export const dashboardGuard: CanActivateFn = () =>
  hasAdminRole(inject(AuthService).getToken()) ? inject(Router).createUrlTree(['/admin']) : true;
