import { marketStatus } from './market-status';

// October 2026: the 15th is a Thursday and the 17th a Saturday; New York is UTC-4
describe('marketStatus', () => {
  it('is open during regular hours on a weekday with recent trades', () => {
    expect(marketStatus(new Date('2026-10-15T14:00:00Z'), '2026-10-15T13:59:30Z')).toBe('open');
  });

  it('opens at 9:30 and closes at 16:00 New York time', () => {
    expect(marketStatus(new Date('2026-10-15T13:29:00Z'), null)).toBe('closed');
    expect(marketStatus(new Date('2026-10-15T13:30:00Z'), null)).toBe('open');
    expect(marketStatus(new Date('2026-10-15T19:59:00Z'), null)).toBe('open');
    expect(marketStatus(new Date('2026-10-15T20:00:00Z'), null)).toBe('closed');
  });

  it('is closed at weekends', () => {
    expect(marketStatus(new Date('2026-10-17T15:00:00Z'), null)).toBe('closed');
  });

  it('reads a weekday with no trades for 30 minutes as closed, such as a holiday', () => {
    expect(marketStatus(new Date('2026-10-15T16:00:00Z'), '2026-10-14T19:59:00Z')).toBe('closed');
    expect(marketStatus(new Date('2026-10-15T16:00:00Z'), '2026-10-15T15:40:00Z')).toBe('open');
  });

  it('falls back to the schedule when there is no market data', () => {
    expect(marketStatus(new Date('2026-10-15T16:00:00Z'), null)).toBe('open');
  });
});
