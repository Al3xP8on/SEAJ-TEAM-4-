import { DOCUMENT } from '@angular/common';
import { Injectable, inject, signal } from '@angular/core';
@Injectable({ providedIn: 'root' })
export class AppearanceService {
  private readonly document = inject(DOCUMENT);
  readonly theme = signal<'light' | 'dark'>('light');
  readonly textSize = signal(100);
  constructor() {
    try {
      const saved = JSON.parse(this.document.defaultView?.localStorage.getItem('seaj-appearance') ?? '{}');
      if (saved.theme === 'dark') this.theme.set('dark');
      if ([100, 125, 150, 200].includes(saved.textSize)) this.textSize.set(saved.textSize);
    } catch { /* Defaults remain available when browser storage is disabled. */ }
    this.apply();
  }
  setTheme(theme: 'light' | 'dark') { this.theme.set(theme); this.apply(); }
  setTextSize(size: number) { if ([100, 125, 150, 200].includes(Number(size))) { this.textSize.set(Number(size)); this.apply(); } }
  private apply() {
    this.document.documentElement.dataset['theme'] = this.theme();
    this.document.documentElement.dataset['textSize'] = String(this.textSize());
    this.document.documentElement.style.fontSize = `${this.textSize()}%`;
    try { this.document.defaultView?.localStorage.setItem('seaj-appearance', JSON.stringify({ theme: this.theme(), textSize: this.textSize() })); } catch { /* Preferences still work for this session. */ }
  }
}
