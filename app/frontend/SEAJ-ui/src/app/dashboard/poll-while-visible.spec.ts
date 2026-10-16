import { pollWhileVisible } from './poll-while-visible';

describe('pollWhileVisible', () => {
  let state: DocumentVisibilityState;

  const setVisibility = (next: DocumentVisibilityState) => {
    state = next;
    document.dispatchEvent(new Event('visibilitychange'));
  };

  beforeEach(() => {
    vi.useFakeTimers();
    state = 'visible';
    vi.spyOn(document, 'visibilityState', 'get').mockImplementation(() => state);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  function count(immediate: boolean) {
    let ticks = 0;
    const subscription = pollWhileVisible(document, 1000, { immediate }).subscribe(() => ticks++);
    return { ticks: () => ticks, stop: () => subscription.unsubscribe() };
  }

  it('ticks on each period while visible, first tick straight away when immediate', () => {
    const poll = count(true);
    expect(poll.ticks()).toBe(1);

    vi.advanceTimersByTime(3000);
    expect(poll.ticks()).toBe(4);
    poll.stop();
  });

  it('waits a full period for the first tick when not immediate', () => {
    const poll = count(false);
    expect(poll.ticks()).toBe(0);

    vi.advanceTimersByTime(1000);
    expect(poll.ticks()).toBe(1);
    poll.stop();
  });

  it('stops while the tab is hidden and catches up as soon as it is visible', () => {
    const poll = count(false);
    vi.advanceTimersByTime(1000);

    setVisibility('hidden');
    vi.advanceTimersByTime(10_000);
    expect(poll.ticks()).toBe(1);

    setVisibility('visible');
    expect(poll.ticks()).toBe(2);
    vi.advanceTimersByTime(1000);
    expect(poll.ticks()).toBe(3);
    poll.stop();
  });

  it('does not start polling in a tab that opens hidden', () => {
    state = 'hidden';
    const poll = count(true);

    vi.advanceTimersByTime(5000);
    expect(poll.ticks()).toBe(0);
    poll.stop();
  });
});
