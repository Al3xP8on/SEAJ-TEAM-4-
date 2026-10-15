import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { classes } from './hlm';

@Component({ template: '', host: { class: 'font-bold' } })
class ClassManagerHost {
  readonly color = signal('text-black');

  constructor() {
    classes(() => ['px-2', this.color()]);
    classes(() => ['py-2']);
  }
}

describe('Spartan class management', () => {
  it('merges multiple sources and replaces reactive classes while keeping base classes', () => {
    const fixture = TestBed.createComponent(ClassManagerHost);
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;
    expect([...element.classList]).toEqual(expect.arrayContaining(['font-bold', 'px-2', 'py-2', 'text-black']));

    fixture.componentInstance.color.set('text-white');
    fixture.detectChanges();
    expect(element.classList.contains('text-black')).toBe(false);
    expect([...element.classList]).toEqual(expect.arrayContaining(['font-bold', 'px-2', 'py-2', 'text-white']));
  });

  it('preserves externally added classes across updates and stops managing destroyed elements', async () => {
    const fixture = TestBed.createComponent(ClassManagerHost);
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;
    document.body.append(element);
    try {
      element.classList.add('external-marker');
      await new Promise(resolve => setTimeout(resolve, 0));
      fixture.componentInstance.color.set('text-white');
      fixture.detectChanges();
      expect(element.classList.contains('external-marker')).toBe(true);

      fixture.destroy();
      element.className = 'after-destroy';
      await new Promise(resolve => setTimeout(resolve, 0));
      expect(element.className).toBe('after-destroy');
    } finally {
      element.remove();
    }
  });
});
