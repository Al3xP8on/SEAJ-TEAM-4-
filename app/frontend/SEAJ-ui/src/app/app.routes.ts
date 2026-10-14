// Public login sits outside the persistent navigation for authenticated pages.
// Redirects old HTML login bookmarks to the same Angular route.
import { AccountComponent } from './account/account';
import { Routes } from '@angular/router';
import { LandingComponent } from './landing/landing';
import { LoginComponent } from './login/login';
import { DashboardComponent } from './dashboard/dashboard';
import { OrdersComponent } from './orders/orders';
import { ShellComponent } from './shell/shell';
import { authGuard } from './auth/auth.guard';

export const routes: Routes = [
  { path: '', component: LandingComponent, pathMatch: 'full' },
  { path: 'login/Login.html', redirectTo: 'login', pathMatch: 'full' },
  { path: 'login', component: LoginComponent },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    canActivateChild: [authGuard],
    children: [
      { path: 'account', component: AccountComponent, title: 'My Account | SEAJ Capital' },
      { path: 'dashboard', component: DashboardComponent },
      { path: 'order', component: OrdersComponent },
      { path: 'blotter', component: OrdersComponent },
    ],
  },
];
