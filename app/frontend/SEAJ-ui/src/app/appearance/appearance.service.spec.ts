import { TestBed } from '@angular/core/testing';
import { AppearanceService } from './appearance.service';
describe('Application appearance', () => {
  beforeEach(() => { localStorage.removeItem('seaj-appearance'); });
  afterEach(() => { localStorage.removeItem('seaj-appearance'); document.documentElement.classList.remove('dark'); document.documentElement.style.fontSize = ''; document.documentElement.dataset['theme'] = 'light'; document.documentElement.dataset['textSize'] = '100'; });
  it('applies and persists dark mode and 200% text at the document root', () => {
    const service = TestBed.inject(AppearanceService); service.setTheme('dark'); service.setTextSize(200);
    expect(document.documentElement.dataset['theme']).toBe('dark'); expect(document.documentElement.classList.contains('dark')).toBe(true); expect(document.documentElement.style.fontSize).toBe('200%');
    expect(JSON.parse(localStorage.getItem('seaj-appearance')!)).toEqual({ theme: 'dark', textSize: 200 });
  });
  it('clears the root dark class when switching back to light mode', () => {
    const service = TestBed.inject(AppearanceService); service.setTheme('dark'); service.setTheme('light');
    expect(document.documentElement.classList.contains('dark')).toBe(false); expect(document.documentElement.dataset['theme']).toBe('light');
  });
  it('restores saved preferences and ignores unsupported sizes', () => {
    localStorage.setItem('seaj-appearance', JSON.stringify({ theme: 'dark', textSize: 150 }));
    const service = TestBed.inject(AppearanceService); expect(service.textSize()).toBe(150); expect(document.documentElement.classList.contains('dark')).toBe(true); service.setTextSize(500); expect(service.textSize()).toBe(150);
  });
});
