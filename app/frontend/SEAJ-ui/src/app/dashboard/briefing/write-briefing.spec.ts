import { BriefingFacts, writeBriefing } from './write-briefing';

const FACTS: BriefingFacts = {
  totalValue: 20000,
  dayChange: { amount: 150, percent: 0.0075 },
  atCost: false,
  holdings: [
    { symbol: 'AAPL', value: 4000, dayChange: 100 },
    { symbol: 'MSFT', value: 4000, dayChange: -180 },
    { symbol: 'NVDA', value: 2000, dayChange: null },
  ],
  buyingPower: 10000,
  reserved: 0,
  openOrders: 0,
  concentrationLimit: 0.25,
};

describe('writeBriefing', () => {
  it('summarises value, the day, the biggest mover, diversification and cash', () => {
    expect(writeBriefing(FACTS)).toEqual([
      'Your portfolio is worth $20,000.00, up $150.00 (0.75%) today.',
      'MSFT moved the most today, down $180.00.',
      'No single holding is above the 25% guideline.',
      'You have $10,000.00 available to trade.',
    ]);
  });

  it('says "down" for a losing day, without a minus sign', () => {
    const [first] = writeBriefing({ ...FACTS, dayChange: { amount: -42.5, percent: -0.002 } });

    expect(first).toBe('Your portfolio is worth $20,000.00, down $42.50 (0.20%) today.');
  });

  it('names one holding above the guideline with its share', () => {
    const sentences = writeBriefing({
      ...FACTS,
      holdings: [{ symbol: 'AAPL', value: 9000, dayChange: null }],
    });

    expect(sentences).toContain('AAPL makes up 45% of your portfolio, above the 25% guideline.');
  });

  it('lists several holdings above the guideline, largest first', () => {
    const sentences = writeBriefing({
      ...FACTS,
      holdings: [
        { symbol: 'MSFT', value: 6000, dayChange: null },
        { symbol: 'AAPL', value: 7000, dayChange: null },
        { symbol: 'NVDA', value: 5500, dayChange: null },
      ],
    });

    expect(sentences).toContain('AAPL, MSFT and NVDA are each above the 25% guideline.');
  });

  it('explains reserved cash and waiting orders', () => {
    const sentences = writeBriefing({ ...FACTS, reserved: 2500, buyingPower: 7500, openOrders: 2 });

    expect(sentences).toContain(
      'You have $7,500.00 available to trade, after $2,500.00 set aside for open buy orders.',
    );
    expect(sentences.at(-1)).toBe('2 orders are waiting to fill.');
  });

  it('is honest when there are no live prices', () => {
    const [first, second] = writeBriefing({
      ...FACTS,
      atCost: true,
      dayChange: null,
      holdings: FACTS.holdings.map((h) => ({ ...h, dayChange: null })),
    });

    expect(first).toBe(
      "Your portfolio is worth $20,000.00, with holdings valued at what you paid, as live prices aren't available.",
    );
    expect(second).toBe('No single holding is above the 25% guideline.');
  });

  it('keeps it short for an empty portfolio', () => {
    expect(
      writeBriefing({ ...FACTS, holdings: [], totalValue: 500, buyingPower: 500, openOrders: 1 }),
    ).toEqual([
      "You don't hold any investments yet. You have $500.00 available to trade.",
      '1 order is waiting to fill.',
    ]);
  });
});
