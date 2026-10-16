import { isPlatformBrowser } from '@angular/common';
import {
  DestroyRef,
  effect,
  ElementRef,
  HostAttributeToken,
  inject,
  Injector,
  PLATFORM_ID,
  runInInjectionContext,
} from '@angular/core';
import { type ClassValue, clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

export function hlm(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

// Global map to track class managers per element
const elementClassManagers = new WeakMap<HTMLElement, ElementClassManager>();
// Global mutation observer for all elements
let globalObserver: MutationObserver | null = null;
const observedElements = new Set<HTMLElement>();

interface ElementClassManager {
  element: HTMLElement;
  sources: Map<number, { classes: Set<string>; order: number }>;
  baseClasses: Set<string>;
  isUpdating: boolean;
  nextOrder: number;
  hasInitialized: boolean;
  restoreRafId: number | null;
  /** Transitions are suppressed until the first effect writes correct classes */
  transitionsSuppressed: boolean;
  /** Original inline transition value to restore after suppression (empty string = none was set) */
  previousTransition: string;
  /** Original inline transition priority to preserve !important when restoring */
  previousTransitionPriority: string;
}

let sourceCounter = 0;

/**
 * This function dynamically adds and removes classes for a given element without requiring
 * the a class binding (e.g. `[class]="..."`) which may interfere with other class bindings.
 *
 * 1. This will merge the existing classes on the element with the new classes.
 * 2. It will also remove any classes that were previously added by this function but are no longer present in the new classes.
 * 3. Multiple calls to this function on the same element will be merged efficiently.
 */
export function classes(computed: () => ClassValue[] | string, options: ClassesOptions = {}) {
  runInInjectionContext(options.injector ?? inject(Injector), () => {
    const elementRef = options.elementRef ?? inject(ElementRef);
    const platformId = inject(PLATFORM_ID);
    const destroyRef = inject(DestroyRef);
    const baseClasses = inject(new HostAttributeToken('class'), { optional: true });

    const element = elementRef.nativeElement;

    // Create unique identifier for this source
    const sourceId = sourceCounter++;

    // Get or create the class manager for this element
    let manager = elementClassManagers.get(element);

    if (!manager) {
      // Initialize base classes from variation (host attribute 'class')
      const initialBaseClasses = new Set<string>();

      if (baseClasses) {
        toClassList(baseClasses).forEach((cls) => initialBaseClasses.add(cls));
      }

      manager = {
        element,
        sources: new Map(),
        baseClasses: initialBaseClasses,
        isUpdating: false,
        nextOrder: 0,
        hasInitialized: false,
        restoreRafId: null,
        transitionsSuppressed: false,
        previousTransition: '',
        previousTransitionPriority: '',
      };
      elementClassManagers.set(element, manager);

      // Setup global observer if needed and register this element
      setupGlobalObserver(platformId);
      observedElements.add(element);

      // Suppress transitions until the first effect writes correct classes and
      // the browser has painted them. This prevents CSS transition animations
      // during hydration when classes change from SSR state to client state.
      if (isPlatformBrowser(platformId)) {
        manager.previousTransition = element.style.getPropertyValue('transition');
        manager.previousTransitionPriority = element.style.getPropertyPriority('transition');
        element.style.setProperty('transition', 'none', 'important');
        manager.transitionsSuppressed = true;
      }
    }

    // Capture the initialized manager for effects and cleanup callbacks.
    const activeManager = manager;
    const sourceOrder = activeManager.nextOrder++;

    function updateClasses(): void {
      // Get the new classes from the computed function
      const newClasses = toClassList(computed());

      // Update this source's classes, keeping the original order
      activeManager.sources.set(sourceId, {
        classes: new Set(newClasses),
        order: sourceOrder,
      });

      // Update the element
      updateElement(activeManager);

      // Re-enable transitions after the first effect writes correct classes.
      // Deferred to next animation frame so the browser paints the class change
      // with transitions disabled first, then re-enables them.
      if (activeManager.transitionsSuppressed) {
        activeManager.transitionsSuppressed = false;
        activeManager.restoreRafId = requestAnimationFrame(() => {
          activeManager.restoreRafId = null;
          restoreTransitionSuppression(activeManager);
        });
      }
    }

    // Register cleanup with DestroyRef
    destroyRef.onDestroy(() => {
      if (activeManager.restoreRafId !== null) {
        cancelAnimationFrame(activeManager.restoreRafId);
        activeManager.restoreRafId = null;
      }

      if (activeManager.transitionsSuppressed) {
        activeManager.transitionsSuppressed = false;
        restoreTransitionSuppression(activeManager);
      }

      // Remove this source from the manager
      activeManager.sources.delete(sourceId);

      // If no more sources, clean up the manager
      if (activeManager.sources.size === 0) {
        cleanupManager(element);
      } else {
        // Update element without this source's classes
        updateElement(activeManager);
      }
    });

    /**
     * We need this effect to track changes to the computed classes. Ideally, we would use
     * afterRenderEffect here, but that doesn't run in SSR contexts, so we use a standard
     * effect which works in both browser and SSR.
     */
    effect(updateClasses);
  });
}

function restoreTransitionSuppression(manager: ElementClassManager): void {
  const prev = manager.previousTransition;
  if (prev) {
    manager.element.style.setProperty(
      'transition',
      prev,
      manager.previousTransitionPriority || undefined,
    );
  } else {
    manager.element.style.removeProperty('transition');
  }
}

function collectSourceClasses(manager: ElementClassManager): Set<string> {
  const sourceClasses = new Set<string>();
  for (const source of manager.sources.values()) {
    for (const className of source.classes) {
      sourceClasses.add(className);
    }
  }
  return sourceClasses;
}

function handleClassMutation(mutation: MutationRecord): void {
  if (mutation.type !== 'attributes' || mutation.attributeName !== 'class') return;
  const element = mutation.target;
  if (!(element instanceof HTMLElement)) return;
  const manager = elementClassManagers.get(element);
  if (!manager || !observedElements.has(element) || manager.isUpdating) return;

  // Preserve external classes, excluding classes supplied by registered sources.
  const sourceClasses = collectSourceClasses(manager);
  manager.baseClasses.clear();
  for (const className of toClassList(element.className)) {
    if (!sourceClasses.has(className)) manager.baseClasses.add(className);
  }
  updateElement(manager);
}

function setupGlobalObserver(platformId: Parameters<typeof isPlatformBrowser>[0]): void {
  if (!isPlatformBrowser(platformId) || globalObserver) return;
  globalObserver = new MutationObserver((mutations) => {
    mutations.forEach(handleClassMutation);
  });
  globalObserver.observe(document, {
    attributes: true,
    attributeFilter: ['class'],
    subtree: true,
  });
}

function updateElement(manager: ElementClassManager): void {
  if (manager.isUpdating) return; // Prevent recursive updates

  manager.isUpdating = true;

  // Handle initialization: capture base classes after first source registration
  if (!manager.hasInitialized && manager.sources.size > 0) {
    // Get current classes on element (may include SSR classes)
    const currentClasses = toClassList(manager.element.className);

    // Get all classes that will be applied by sources
    const allSourceClasses = new Set<string>();
    for (const source of manager.sources.values()) {
      source.classes.forEach((className) => allSourceClasses.add(className));
    }

    // Only consider classes as "base" if they're not produced by any source
    // This prevents SSR-rendered classes from being preserved as base classes
    currentClasses.forEach((className) => {
      if (!allSourceClasses.has(className)) {
        manager.baseClasses.add(className);
      }
    });

    manager.hasInitialized = true;
  }

  // Get classes from all sources, sorted by registration order (later takes precedence)
  const sortedSources = Array.from(manager.sources.entries()).sort(
    ([, a], [, b]) => a.order - b.order,
  );

  const allSourceClasses: string[] = [];
  for (const [, source] of sortedSources) {
    allSourceClasses.push(...source.classes);
  }

  // Combine base classes with all source classes, ensuring base classes take precedence
  const classesToApply =
    allSourceClasses.length > 0 || manager.baseClasses.size > 0
      ? hlm([...allSourceClasses, ...manager.baseClasses])
      : '';

  // Apply the classes to the element
  if (manager.element.className !== classesToApply) {
    manager.element.className = classesToApply;
  }

  manager.isUpdating = false;
}

function cleanupManager(element: HTMLElement): void {
  // Remove from global tracking
  observedElements.delete(element);
  elementClassManagers.delete(element);

  // If no more elements being tracked, cleanup global observer
  if (observedElements.size === 0 && globalObserver) {
    globalObserver.disconnect();
    globalObserver = null;
  }
}

interface ClassesOptions {
  elementRef?: ElementRef<HTMLElement>;
  injector?: Injector;
}

// Cache for parsed class lists to avoid repeated string operations
const classListCache = new Map<string, string[]>();

function toClassList(className: string | ClassValue[]): string[] {
  // For simple string inputs, use cache to avoid repeated parsing
  if (typeof className === 'string') {
    const cached = classListCache.get(className);
    if (cached) return cached;
  }

  const result = clsx(className)
    .split(' ')
    .filter((c) => c.length > 0);

  // Cache string results, but limit cache size to prevent memory growth
  if (typeof className === 'string' && classListCache.size < 1000) {
    classListCache.set(className, result);
  }

  return result;
}
