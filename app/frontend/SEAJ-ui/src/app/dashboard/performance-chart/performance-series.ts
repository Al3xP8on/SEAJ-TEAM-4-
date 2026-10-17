import { MarketChart } from '../../orders/orders.models';

export type Period = '1D' | '1W' | '1M';

export interface PerformanceHolding {
  symbol: string;
  quantity: number;
}

export interface SeriesPoint {
  time: string;
  value: number;
}

export interface PerformanceSeries {
  points: SeriesPoint[];
  /** Held symbols with no price history, left out of the line */
  missing: string[];
}

const DAY_MS = 24 * 60 * 60 * 1000;
const NEW_YORK_DATE = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/New_York' });

/** 1D uses minute bars from the latest session; longer periods use daily bars */
export const timeframeFor = (period: Period): '1Min' | '1Day' =>
  period === '1D' ? '1Min' : '1Day';

/**
 * Values today's holdings at each past price: quantity x close, summed across holdings.
 * Gaps in one symbol's bars carry its last close forward, and the line starts once every
 * included symbol has a price, so a late first bar never shows as a fake jump.
 */
export function buildSeries(
  holdings: PerformanceHolding[],
  charts: (MarketChart | null)[],
  period: Period,
): PerformanceSeries {
  const missing: string[] = [];
  const included = holdings.flatMap((holding, index) => {
    const bars = charts[index]?.bars ?? [];
    if (bars.length === 0) {
      missing.push(holding.symbol);
      return [];
    }
    const sorted = [...bars].sort((a, b) => a.timestamp.localeCompare(b.timestamp));
    return [{ quantity: holding.quantity, bars: sorted }];
  });
  if (included.length === 0) {
    return { points: [], missing };
  }

  const latest = Math.max(...included.map((h) => Date.parse(h.bars.at(-1)!.timestamp)));
  const inPeriod = (timestamp: string) => {
    if (period === '1D') {
      return sessionDate(timestamp) === sessionDate(new Date(latest).toISOString());
    }
    return Date.parse(timestamp) >= latest - (period === '1W' ? 7 : 30) * DAY_MS;
  };
  const windowed = included.map((h) => ({
    ...h,
    bars: h.bars.filter((bar) => inPeriod(bar.timestamp)),
  }));

  const times = [...new Set(windowed.flatMap((h) => h.bars.map((bar) => bar.timestamp)))].sort();
  const cursors = windowed.map(() => -1);
  const points: SeriesPoint[] = [];
  for (const time of times) {
    let value = 0;
    let complete = true;
    windowed.forEach((h, i) => {
      while (cursors[i] + 1 < h.bars.length && h.bars[cursors[i] + 1].timestamp <= time) {
        cursors[i]++;
      }
      if (cursors[i] < 0) {
        complete = false;
      } else {
        value += h.quantity * h.bars[cursors[i]].close;
      }
    });
    if (complete) {
      points.push({ time, value });
    }
  }
  return { points, missing };
}

/** Trading days are New York dates, whatever the viewer's time zone */
function sessionDate(timestamp: string): string {
  return NEW_YORK_DATE.format(new Date(timestamp));
}
