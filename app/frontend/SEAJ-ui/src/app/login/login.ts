// Connects Angular forms to username/password authentication and both email verification steps.
// Preserves validation, password clearing, retry feedback and cancellation of late UI responses.
import { Component, effect, signal, OnDestroy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import type { Challenge } from '../auth/session-client.mjs';

type MessageType = 'success' | 'error' | '';
// Optional phone: same rule as the auth service (fits the accounts.phone VARCHAR(20) column)
export const PHONE_PATTERN = /^(?=.{7,20}$)\+?[0-9 ()-]+$/;

@Component({
  selector: 'app-login', imports: [FormsModule, RouterLink], templateUrl: './login.html', styleUrl: './login.css',
})
export class LoginComponent implements OnDestroy {
  view = signal<'login' | 'register' | 'verify'>('login');
  username = ''; password = ''; regUsername = ''; regPassword = ''; regEmail = ''; regPhone = ''; code = '';
  loginMessage = signal(''); loginMessageType = signal<MessageType>(''); loginLoading = signal(false);
  registerMessage = signal(''); registerMessageType = signal<MessageType>(''); registerLoading = signal(false);
  verificationMessage = signal(''); verificationLoading = signal(false);
  private verification: { purpose: 'LOGIN' | 'REGISTER'; id: string } | null = null;
  private generation = 0;
  constructor(private auth: AuthService, private router: Router) {
    effect(() => {
      if (this.auth.sessionExpired()) {
        this.showView('login');
        this.loginMessage.set('Your session has expired. Please log in again.');
        this.loginMessageType.set('error');
      }
    });
  }
  showView(view: 'login' | 'register') {
    this.generation++;
    this.verification = null;
    this.code = ''; this.password = ''; this.regPassword = '';
    this.view.set(view);
  }
  cancelVerification() { this.auth.logout(); this.showView('login'); }
  private showVerification(purpose: 'LOGIN' | 'REGISTER', challenge: Challenge) {
    this.verification = { purpose, id: challenge.challenge_id };
    this.code = '';
    this.verificationMessage.set(challenge.message || 'Enter the six-digit code from your email.');
    this.view.set('verify');
  }
  private message(error: unknown) {
    return error instanceof Error ? error.message : 'Authentication failed. Please try again.';
  }
  onLogin() {
    if (this.loginLoading()) return;
    const username = this.username.trim();
    if (username.length < 3 || username.length > 100 || !this.password) {
      this.loginMessage.set('Enter your username (3–100 characters) and password.');
      this.loginMessageType.set('error'); return;
    }
    const generation = this.generation;
    this.loginLoading.set(true); this.loginMessage.set('Sending verification code…'); this.loginMessageType.set('');
    this.auth.login(username, this.password).subscribe({
      next: challenge => {
        this.password = ''; this.loginLoading.set(false);
        if (generation === this.generation) this.showVerification('LOGIN', challenge);
      },
      error: error => {
        this.loginLoading.set(false);
        if (generation === this.generation) {
          this.loginMessage.set(this.message(error)); this.loginMessageType.set('error');
        }
      },
    });
  }
  onRegister() {
    if (this.registerLoading()) return;
    const username = this.regUsername.trim(); const email = this.regEmail.trim();
    if (username.length < 3 || username.length > 100 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)
      || this.regPassword.length < 8 || new TextEncoder().encode(this.regPassword).length > 72) {
      this.registerMessage.set('Enter a unique username (3–100 characters), a valid email and a password of at least 8 characters (maximum 72 bytes).');
      this.registerMessageType.set('error'); return;
    }
    const phone = this.regPhone.trim();
    if (phone && !PHONE_PATTERN.test(phone)) {
      this.registerMessage.set('Enter a valid phone number (7–20 digits, spaces, dashes or brackets, optional leading +), or leave it blank.');
      this.registerMessageType.set('error'); return;
    }
    const generation = this.generation;
    this.registerLoading.set(true); this.registerMessage.set('Sending verification code…'); this.registerMessageType.set('');
    this.auth.register({ username, email, password: this.regPassword, ...(phone ? { phone } : {}) }).subscribe({
      next: challenge => {
        this.regPassword = ''; this.registerLoading.set(false);
        if (generation === this.generation) this.showVerification('REGISTER', challenge);
      },
      error: error => {
        this.registerLoading.set(false);
        if (generation === this.generation) {
          this.registerMessage.set(this.message(error)); this.registerMessageType.set('error');
        }
      },
    });
  }
  onVerify() {
    if (this.verificationLoading()) return;
    const state = this.verification; const code = this.code.trim();
    if (!state || !/^\d{6}$/.test(code)) {
      this.verificationMessage.set('Enter the six-digit code from your email.'); return;
    }
    const generation = this.generation;
    this.verificationLoading.set(true);
    const error = (error: unknown) => {
      this.verificationLoading.set(false);
      if (generation === this.generation) this.verificationMessage.set(this.message(error));
    };
    if (state.purpose === 'LOGIN') {
      this.auth.verifyLogin(code).subscribe({
        next: () => {
          this.verificationLoading.set(false);
          if (generation === this.generation) {
            this.showView('login'); this.loginMessage.set('Login successful.'); this.loginMessageType.set('success');
            this.router.navigate(['/dashboard']);
          }
        }, error,
      });
    } else {
      this.auth.verifyRegistration(state.id, code).subscribe({
        next: user => {
          this.verificationLoading.set(false);
          if (generation === this.generation) {
            this.username = user.username; this.showView('login');
            this.loginMessage.set(`Account ${user.account_id} created. Please log in with your username and password.`);
            this.loginMessageType.set('success');
          }
        }, error,
      });
    }
  }
  ngOnDestroy() {
    this.generation++;
    if (this.loginLoading() || this.verification?.purpose === 'LOGIN') this.auth.logout();
  }
}
