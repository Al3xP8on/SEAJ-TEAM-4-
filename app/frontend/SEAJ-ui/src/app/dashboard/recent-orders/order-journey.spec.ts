import { Order, OrderHistory } from '../../orders/orders.models';
import { orderJourney } from './order-journey';

const ORDER: Order = {
  orderId: 'o1',
  accountId: 'ACC-0001',
  accountHolderName: 'Alice Johnson',
  ticker: 'AAPL',
  instrumentName: 'Apple Inc.',
  side: 'BUY',
  quantity: 10,
  price: 190,
  totalValue: 1900,
  status: 'FILLED',
  createdAt: '2026-10-15T10:02:01.000123',
  orderType: 'MARKET',
  executionPrice: 189.95,
};

const step = (
  status: OrderHistory['status'],
  timestamp: string,
  reason: string | null = null,
): OrderHistory => ({
  orderId: 'o1',
  status,
  timestamp,
  reason,
});

describe('orderJourney', () => {
  it('describes each step with the time it took and the total', () => {
    const journey = orderJourney(
      [
        // Out of order on purpose: steps are sorted by time
        step('FILLED', '2026-10-15T10:02:02.340456'),
        step('NEW', '2026-10-15T10:02:01.000123'),
        step('PENDING', '2026-10-15T10:02:01.040789'),
      ],
      ORDER,
    );

    expect(journey.steps.map((s) => [s.label, s.elapsed])).toEqual([
      ['Order received', null],
      ['Passed checks and sent to market', '+40 ms'],
      ['Filled at $189.95', '+1.3 s'],
    ]);
    expect(journey.total).toBe('Took 1.3 s from placing to filling.');
  });

  it('gives the reason for a rejection', () => {
    const journey = orderJourney(
      [
        step('NEW', '2026-10-15T10:00:00'),
        step('REJECTED', '2026-10-15T10:00:01', 'Insufficient cash'),
      ],
      { ...ORDER, status: 'REJECTED', executionPrice: null },
    );

    expect(journey.steps[1].label).toBe('Rejected: Insufficient cash');
    expect(journey.total).toBe('Took 1.0 s from placing to rejection.');
  });

  it('uses minutes for orders that waited, and has no total while still open', () => {
    const waiting = orderJourney(
      [step('NEW', '2026-10-15T10:00:00'), step('PENDING', '2026-10-15T10:12:00')],
      { ...ORDER, status: 'PENDING', executionPrice: null },
    );

    expect(waiting.steps[1].elapsed).toBe('+12 min');
    expect(waiting.total).toBeNull();
  });
});
