// Routes the Angular application to its login and email verification screen.
// Redirects old HTML login bookmarks to the same Angular route.
import { Routes } from '@angular/router';
import { LoginComponent } from './login/login';
import { DashboardComponent } from './dashboard/dashboard';

export const routes: Routes = [
  { path: 'login/Login.html', redirectTo: 'login', pathMatch: 'full' },
  { path: 'login', component: LoginComponent },
  { path: 'dashboard', component: DashboardComponent },
  { path: '', redirectTo: 'login', pathMatch: 'full' },
];
