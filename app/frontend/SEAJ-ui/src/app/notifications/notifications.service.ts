import { Injectable, OnDestroy, signal } from '@angular/core';

export interface Notification { id: number; title: string; message: string; kind: 'success' | 'info' | 'error'; }

@Injectable({ providedIn: 'root' })
export class NotificationsService implements OnDestroy {
  readonly items = signal<Notification[]>([]);
  private nextId = 0;
  private timers = new Map<number, ReturnType<typeof setTimeout>>();
  show(title: string, message = '', kind: Notification['kind'] = 'success') {
    const id = ++this.nextId;
    this.items.update(items => [...items, { id, title, message, kind }]);
    this.resume(id);
  }
  pause(id: number) { clearTimeout(this.timers.get(id)); this.timers.delete(id); }
  resume(id: number) { this.pause(id); this.timers.set(id, setTimeout(() => this.dismiss(id), 5000)); }
  dismiss(id: number) { this.pause(id); this.items.update(items => items.filter(item => item.id !== id)); }
  clear() {
    for (const id of this.timers.keys()) {
      this.pause(id);
    }
    this.items.set([]);
  }
  ngOnDestroy() { this.clear(); }
}
