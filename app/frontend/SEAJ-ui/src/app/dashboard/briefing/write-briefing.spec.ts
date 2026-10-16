import { BriefingFacts, BriefingItem, writeBriefing } from './write-briefing';

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

const text = (items: BriefingItem[]) => items.map((i) => [i.title, i.detail]);
const find = (items: BriefingItem[], kind: BriefingItem['kind']) =>
  items.find((i) => i.kind === kind)!;

describe('writeBriefing', () => {
  it('summarises value, the day, the biggest mover, diversification and cash', () => {
    expect(text(writeBriefing(FACTS))).toEqual([
      ['Worth $20,000.00', 'Up $150.00 (0.75%) today'],
      ['MSFT moved the most', 'Down $180.00 today'],
      ['Well spread', 'No single holding is above 25% of your portfolio'],
      ['$10,000.00 to trade', null],
    ]);
  });

  it('picks an icon and tone for each fact, with direction also in words', () => {
    const items = writeBriefing(FACTS);

    expect(find(items, 'value')).toMatchObject({ icon: 'lucideTrendingUp', tone: 'good' });
    expect(find(items, 'mover')).toMatchObject({ icon: 'lucideActivity', tone: 'bad' });
    expect(find(items, 'diversification')).toMatchObject({ icon: 'lucideChartPie', tone: 'good' });
    expect(find(items, 'cash')).toMatchObject({ icon: 'lucideWallet', tone: 'neutral' });
  });

  it('says "Down" for a losing day, without a minus sign', () => {
    const value = find(
      writeBriefing({ ...FACTS, dayChange: { amount: -42.5, percent: -0.002 } }),
      'value',
    );

    expect(value).toMatchObject({
      detail: 'Down $42.50 (0.20%) today',
      icon: 'lucideTrendingDown',
      tone: 'bad',
    });
  });

  it('warns about one holding above the guideline with its share', () => {
    const item = find(
      writeBriefing({ ...FACTS, holdings: [{ symbol: 'AAPL', value: 9000, dayChange: null }] }),
      'diversification',
    );

    expect(item).toEqual({
      kind: 'diversification',
      icon: 'lucideTriangleAlert',
      tone: 'warn',
      title: 'AAPL is 45% of your portfolio',
      detail: 'Above the 25% guideline',
    });
  });

  it('lists several holdings above the guideline, largest first', () => {
    const item = find(
      writeBriefing({
        ...FACTS,
        holdings: [
          { symbol: 'MSFT', value: 6000, dayChange: null },
          { symbol: 'AAPL', value: 7000, dayChange: null },
          { symbol: 'NVDA', value: 5500, dayChange: null },
        ],
      }),
      'diversification',
    );

    expect(item.title).toBe('AAPL, MSFT and NVDA are each above 25%');
  });

  it('explains reserved cash and waiting orders', () => {
    const items = writeBriefing({ ...FACTS, reserved: 2500, buyingPower: 7500, openOrders: 2 });

    expect(find(items, 'cash').detail).toBe('After $2,500.00 set aside for open buy orders');
    expect(items.at(-1)).toMatchObject({
      kind: 'orders',
      title: '2 orders waiting to fill',
      icon: 'lucideClock',
    });
  });

  it('is honest when there are no live prices', () => {
    const items = writeBriefing({
      ...FACTS,
      atCost: true,
      dayChange: null,
      holdings: FACTS.holdings.map((h) => ({ ...h, dayChange: null })),
    });

    expect(find(items, 'value').detail).toBe(
      "Valued at what you paid, as live prices aren't available",
    );
    expect(items.some((i) => i.kind === 'mover')).toBe(false);
  });

  it('keeps it short for an empty portfolio', () => {
    expect(
      text(
        writeBriefing({ ...FACTS, holdings: [], totalValue: 500, buyingPower: 500, openOrders: 1 }),
      ),
    ).toEqual([
      ['No investments yet', 'You have $500.00 available to trade.'],
      ['1 order waiting to fill', null],
    ]);
  });
});
