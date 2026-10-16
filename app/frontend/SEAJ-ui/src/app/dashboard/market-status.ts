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
