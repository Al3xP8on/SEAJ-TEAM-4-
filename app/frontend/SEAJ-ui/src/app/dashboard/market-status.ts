export type MarketStatus = 'open' | 'closed';

const NEW_YORK = new Intl.DateTimeFormat('en-US', {
  timeZone: 'America/New_York',
  weekday: 'short',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

// Regular US trading hours, in minutes after midnight New York time
const OPENS = 9 * 60 + 30;
const CLOSES = 16 * 60;

// Within trading hours, no trade for this long means a holiday or halt. Long enough for
// Alpaca's 15-minute delayed SIP feed.
const STALE_MS = 30 * 60_000;

/**
 * Whether the US stock market is open, from the regular schedule and, when market data is
 * available, the time of the latest trade, so holidays read as closed.
 */
export function marketStatus(now: Date, latestTrade: string | null): MarketStatus {
  const parts = Object.fromEntries(NEW_YORK.formatToParts(now).map((p) => [p.type, p.value]));
  if (parts['weekday'] === 'Sat' || parts['weekday'] === 'Sun') {
    return 'closed';
  }
  const minutes = Number(parts['hour']) * 60 + Number(parts['minute']);
  if (minutes < OPENS || minutes >= CLOSES) {
    return 'closed';
  }
  if (latestTrade !== null && now.getTime() - Date.parse(latestTrade) > STALE_MS) {
    return 'closed';
  }
  return 'open';
}

const NEW_YORK_TIME = new Intl.DateTimeFormat('en-US', {
  timeZone: 'America/New_York',
  weekday: 'short',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hourCycle: 'h23',
});

const WEEKDAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

/** New York weekday (0 = Sunday) and seconds since midnight */
function newYork(date: Date): { day: number; seconds: number } {
  const parts = Object.fromEntries(NEW_YORK_TIME.formatToParts(date).map((p) => [p.type, p.value]));
  return {
    day: WEEKDAYS.indexOf(parts['weekday']),
    seconds: Number(parts['hour']) * 3600 + Number(parts['minute']) * 60 + Number(parts['second']),
  };
}

/**
 * When the market next closes (if open) or opens (if closed), by the regular schedule. A closed
 * weekday during trading hours, such as a holiday, counts on to the next weekday's open.
 */
export function nextChange(now: Date, status: MarketStatus): Date {
  const { day, seconds } = newYork(now);
  if (status === 'open') {
    return new Date(now.getTime() + (CLOSES * 60 - seconds) * 1000);
  }
  let days = 0;
  if (day === 0 || day === 6 || seconds >= OPENS * 60) {
    do {
      days++;
    } while ((day + days) % 7 === 0 || (day + days) % 7 === 6);
  }
  const target = new Date(now.getTime() + (days * 86_400 + OPENS * 60 - seconds) * 1000);
  // Across a daylight-saving change the day is an hour shorter or longer: correct to 09:30
  const drift = OPENS * 60 - newYork(target).seconds;
  return new Date(target.getTime() + drift * 1000);
}

/** "45 min", "2 h 15 min" */
export function countdown(ms: number): string {
  const minutes = Math.max(1, Math.ceil(ms / 60_000));
  if (minutes < 60) {
    return `${minutes} min`;
  }
  const hours = Math.floor(minutes / 60);
  return minutes % 60 === 0 ? `${hours} h` : `${hours} h ${minutes % 60} min`;
}
