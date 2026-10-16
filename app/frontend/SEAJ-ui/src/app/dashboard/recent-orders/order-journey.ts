import { Order, OrderHistory } from '../../orders/orders.models';

export interface JourneyStep {
  label: string;
  time: string;
  /** Time since the previous step, e.g. "+1.2 s"; null for the first */
  elapsed: string | null;
}

export interface Journey {
  steps: JourneyStep[];
  /** e.g. "Took 1.3 s from placing to filling."; null while the order is still open */
  total: string | null;
}

const usd = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });

/** The order's status history as plain-language steps with the time each one took */
export function orderJourney(history: OrderHistory[], order: Order): Journey {
  const sorted = [...history].sort((a, b) => parse(a.timestamp) - parse(b.timestamp));
  const steps = sorted.map((entry, i) => ({
    label: describe(entry, order),
    time: entry.timestamp,
    elapsed:
      i === 0 ? null : `+${duration(parse(entry.timestamp) - parse(sorted[i - 1].timestamp))}`,
  }));

  const last = sorted.at(-1);
  const outcome =
    last?.status === 'FILLED'
      ? 'filling'
      : last?.status === 'REJECTED'
        ? 'rejection'
        : last?.status === 'CANCELLED'
          ? 'cancellation'
          : null;
  const total =
    outcome && sorted.length > 1
      ? `Took ${duration(parse(last!.timestamp) - parse(sorted[0].timestamp))} from placing to ${outcome}.`
      : null;
  return { steps, total };
}

function describe(entry: OrderHistory, order: Order): string {
  switch (entry.status) {
    case 'NEW':
      return 'Order received';
    case 'PENDING':
      return 'Passed checks and sent to market';
    case 'FILLED':
      return order.executionPrice === null
        ? 'Filled'
        : `Filled at ${usd.format(order.executionPrice)}`;
    case 'REJECTED':
      return entry.reason ? `Rejected: ${entry.reason}` : 'Rejected';
    case 'CANCELLED':
      return 'Cancelled';
  }
}

function duration(ms: number): string {
  if (ms < 1000) {
    return `${Math.max(0, Math.round(ms))} ms`;
  }
  if (ms < 60_000) {
    return `${(ms / 1000).toFixed(1)} s`;
  }
  if (ms < 3_600_000) {
    return `${Math.round(ms / 60_000)} min`;
  }
  return `${(ms / 3_600_000).toFixed(1)} h`;
}

/** The backend sends microseconds; keep milliseconds so every browser parses it */
function parse(timestamp: string): number {
  return Date.parse(timestamp.replace(/(\.\d{3})\d+/, '$1'));
}
