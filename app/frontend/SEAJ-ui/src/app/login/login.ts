import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../auth/auth.service';

type MessageType = 'success' | 'error' | '';

@Component({
  selector: 'app-login',
  imports: [FormsModule],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class LoginComponent {
  view = signal<'login' | 'register'>('login');

  username = '';
  password = '';
  loginMessage = signal('');
  loginMessageType = signal<MessageType>('');
  loginLoading = signal(false);

  regUsername = '';
  regPassword = '';
  regEmail = '';
  regPhone = '';
  registerMessage = signal('');
  registerMessageType = signal<MessageType>('');
  registerLoading = signal(false);

  constructor(private auth: AuthService) {}

  onLogin() {
    if (!this.username || this.username.length < 3) {
      this.loginMessage.set('Username must be at least 3 characters.');
      this.loginMessageType.set('error');
      return;
    }
    if (!this.password || this.password.length < 8) {
      this.loginMessage.set('Password must be at least 8 characters.');
      this.loginMessageType.set('error');
      return;
    }

    this.loginLoading.set(true);
    this.loginMessage.set('');

    this.auth.login(this.username, this.password).subscribe({
      next: (data) => {
        const token = this.auth.extractToken(data);
        if (!token) {
          this.loginMessage.set('Login succeeded but no token was returned.');
          this.loginMessageType.set('error');
        } else {
          this.loginMessage.set(`Logged in. Token starts: ${token.slice(0, 20)}...`);
          this.loginMessageType.set('success');
        }
        this.loginLoading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        const msg = err.error?.message ?? `Login failed with status ${err.status}`;
        this.loginMessage.set(`Login failed: ${msg}`);
        this.loginMessageType.set('error');
        this.loginLoading.set(false);
      },
    });
  }

  onRegister() {
    if (!this.regUsername || this.regUsername.length < 3) {
      this.registerMessage.set('Username must be at least 3 characters.');
      this.registerMessageType.set('error');
      return;
    }
    if (!this.regPassword || this.regPassword.length < 8) {
      this.registerMessage.set('Password must be at least 8 characters.');
      this.registerMessageType.set('error');
      return;
    }
    if (this.regEmail && !this.regEmail.match(/^[^\s@]+@[^\s@]+\.[^\s@]+$/)) {
      this.registerMessage.set('Please enter a valid email address.');
      this.registerMessageType.set('error');
      return;
    }

    this.registerLoading.set(true);
    this.registerMessage.set('');

    this.auth
      .register({
        username: this.regUsername,
        password: this.regPassword,
        email: this.regEmail || null,
        phone: this.regPhone || null,
      })
      .subscribe({
        next: (data) => {
          const id = data?.accountId ? ` (${data.accountId})` : '';
          this.registerMessage.set(`Account created successfully${id}. You can log in now.`);
          this.registerMessageType.set('success');
          this.registerLoading.set(false);
        },
        error: (err: HttpErrorResponse) => {
          const msg = err.error?.message ?? `Account creation failed with status ${err.status}`;
          this.registerMessage.set(msg);
          this.registerMessageType.set('error');
          this.registerLoading.set(false);
        },
      });
  }
}
