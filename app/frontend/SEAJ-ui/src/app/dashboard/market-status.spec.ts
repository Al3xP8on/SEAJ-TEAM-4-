import { countdown, describeNextChange, marketStatus, nextChange } from './market-status';

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

describe('nextChange and countdown', () => {
  const after = (now: string, status: 'open' | 'closed') => {
    const from = new Date(now);
    return {
      at: nextChange(from, status).toISOString(),
      in: countdown(nextChange(from, status).getTime() - from.getTime()),
    };
  };

  it('counts down to the close while open', () => {
    // Thursday 10:45 New York
    expect(after('2026-10-15T14:45:00Z', 'open')).toEqual({
      at: '2026-10-15T20:00:00.000Z',
      in: '5 h 15 min',
    });
  });

  it('counts down to the same morning before the open', () => {
    expect(after('2026-10-15T13:00:00Z', 'closed')).toEqual({
      at: '2026-10-15T13:30:00.000Z',
      in: '30 min',
    });
  });

  it('skips to Monday after the Friday close', () => {
    // Friday 17:00 New York
    expect(after('2026-10-16T21:00:00Z', 'closed').at).toBe('2026-10-19T13:30:00.000Z');
  });

  it('moves on to the next day when a weekday reads as closed, such as a holiday', () => {
    expect(after('2026-10-15T16:00:00Z', 'closed').at).toBe('2026-10-16T13:30:00.000Z');
  });

  it('opens at 09:30 New York time across the daylight-saving change', () => {
    // Clocks go back on Sunday 1 November 2026: New York moves from UTC-4 to UTC-5
    expect(after('2026-10-30T21:00:00Z', 'closed').at).toBe('2026-11-02T14:30:00.000Z');
  });
});

describe('describeNextChange', () => {
  it('words the countdown for open, soon-to-open and far-off opens', () => {
    expect(describeNextChange(new Date('2026-10-15T14:45:00Z'), 'open')).toBe(
      'closes in 5 h 15 min',
    );
    expect(describeNextChange(new Date('2026-10-15T13:00:00Z'), 'closed')).toBe('opens in 30 min');
    // Friday 17:00 New York: Monday is more than a day away
    expect(describeNextChange(new Date('2026-10-16T21:00:00Z'), 'closed')).toBe(
      'opens Mon 09:30 ET',
    );
  });
});
