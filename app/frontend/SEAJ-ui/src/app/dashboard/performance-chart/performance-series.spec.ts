import { MarketBar, MarketChart } from '../../orders/orders.models';
import { buildSeries, timeframeFor } from './performance-series';

const bar = (timestamp: string, close: number): MarketBar => ({
  timestamp,
  open: close,
  high: close,
  low: close,
  close,
  volume: 100,
});

const chart = (symbol: string, bars: MarketBar[]): MarketChart => ({
  symbol,
  feed: 'iex',
  timeframe: '1Day',
  bars,
  message: '',
});

describe('buildSeries', () => {
  it('values the holdings at each date as quantity x close, summed', () => {
    const series = buildSeries(
      [
        { symbol: 'AAPL', quantity: 10 },
        { symbol: 'MSFT', quantity: 2 },
      ],
      [
        chart('AAPL', [bar('2026-10-13T04:00:00Z', 100), bar('2026-10-14T04:00:00Z', 110)]),
        chart('MSFT', [bar('2026-10-13T04:00:00Z', 400), bar('2026-10-14T04:00:00Z', 390)]),
      ],
      '1M',
    );

    expect(series.points).toEqual([
      { time: '2026-10-13T04:00:00Z', value: 1800 }, // 10 x 100 + 2 x 400
      { time: '2026-10-14T04:00:00Z', value: 1880 }, // 10 x 110 + 2 x 390
    ]);
    expect(series.missing).toEqual([]);
  });

  it('carries a missing bar forward and starts once every holding has a price', () => {
    const series = buildSeries(
      [
        { symbol: 'AAPL', quantity: 1 },
        { symbol: 'MSFT', quantity: 1 },
      ],
      [
        chart('AAPL', [
          bar('2026-10-12T04:00:00Z', 90),
          bar('2026-10-13T04:00:00Z', 100),
          bar('2026-10-14T04:00:00Z', 110),
        ]),
        // MSFT starts a day later and skips the 14th
        chart('MSFT', [bar('2026-10-13T04:00:00Z', 400), bar('2026-10-15T04:00:00Z', 410)]),
      ],
      '1M',
    );

    expect(series.points.map((p) => p.value)).toEqual([500, 510, 520]);
  });

  it('keeps only the latest New York session for 1D', () => {
    const series = buildSeries(
      [{ symbol: 'AAPL', quantity: 1 }],
      [
        chart('AAPL', [
          bar('2026-10-14T19:59:00Z', 98), // 15:59 in New York on the 14th
          bar('2026-10-15T13:30:00Z', 100), // 09:30 on the 15th
          bar('2026-10-15T19:59:00Z', 105),
        ]),
      ],
      '1D',
    );

    expect(series.points.map((p) => p.value)).toEqual([100, 105]);
  });

  it('keeps the last seven days for 1W', () => {
    const series = buildSeries(
      [{ symbol: 'AAPL', quantity: 1 }],
      [
        chart('AAPL', [
          bar('2026-10-01T04:00:00Z', 80),
          bar('2026-10-09T04:00:00Z', 90),
          bar('2026-10-15T04:00:00Z', 100),
        ]),
      ],
      '1W',
    );

    expect(series.points.map((p) => p.value)).toEqual([90, 100]);
  });

  it('leaves out holdings with no price history and names them', () => {
    const series = buildSeries(
      [
        { symbol: 'AAPL', quantity: 1 },
        { symbol: 'XYZ', quantity: 5 },
      ],
      [chart('AAPL', [bar('2026-10-14T04:00:00Z', 100), bar('2026-10-15T04:00:00Z', 101)]), null],
      '1M',
    );

    expect(series.points.map((p) => p.value)).toEqual([100, 101]);
    expect(series.missing).toEqual(['XYZ']);
  });

  it('uses minute bars for 1D and daily bars otherwise', () => {
    expect(timeframeFor('1D')).toBe('1Min');
    expect(timeframeFor('1W')).toBe('1Day');
    expect(timeframeFor('1M')).toBe('1Day');
  });
});
