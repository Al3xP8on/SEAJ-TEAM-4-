import { BriefingFacts, BriefingItem, writeBriefing } from './write-briefing';

const FACTS: BriefingFacts = {
  totalValue: 20000,
  holdings: [
    { symbol: 'AAPL', value: 4000, dayChange: 100 },
    { symbol: 'MSFT', value: 4000, dayChange: -180 },
    { symbol: 'NVDA', value: 2000, dayChange: null },
  ],
  cash: 10000,
  reserved: 0,
  openOrders: 0,
  concentrationLimit: 0.25,
};

const text = (items: BriefingItem[]) => items.map((i) => [i.title, i.detail]);
const find = (items: BriefingItem[], kind: BriefingItem['kind']) =>
  items.find((i) => i.kind === kind);

describe('writeBriefing', () => {
  it('only says what the summary cards do not: the biggest mover and diversification', () => {
    expect(text(writeBriefing(FACTS))).toEqual([
      ['MSFT moved the most', 'Down $180.00 today'],
      ['Well spread', 'No single holding is above 25% of your portfolio'],
    ]);
  });

  it('picks an icon and tone for each insight, with direction also in words', () => {
    const items = writeBriefing(FACTS);

    expect(find(items, 'mover')).toMatchObject({ icon: 'lucideTrendingDown', tone: 'bad' });
    expect(find(items, 'diversification')).toMatchObject({ icon: 'lucideChartPie', tone: 'good' });
  });

  it('says "Up" for a rising mover, without a plus sign', () => {
    const items = writeBriefing({
      ...FACTS,
      holdings: [{ symbol: 'AAPL', value: 4000, dayChange: 250 }],
    });

    expect(find(items, 'mover')).toMatchObject({
      title: 'AAPL moved the most',
      detail: 'Up $250.00 today',
      icon: 'lucideTrendingUp',
      tone: 'good',
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

    expect(item?.title).toBe('AAPL, MSFT and NVDA are each above 25%');
  });

  it('mentions cash only when open buy orders hold some back', () => {
    expect(find(writeBriefing(FACTS), 'cash')).toBeUndefined();

    const items = writeBriefing({ ...FACTS, reserved: 2500, openOrders: 2 });
    expect(find(items, 'cash')).toMatchObject({
      title: '$2,500.00 set aside',
      detail: 'Held for open buy orders until they fill',
      tone: 'neutral',
    });
    expect(items.at(-1)).toMatchObject({ kind: 'orders', title: '2 orders waiting to fill' });
  });

  it('warns when open buy orders exceed the cash balance', () => {
    expect(find(writeBriefing({ ...FACTS, cash: 100, reserved: 250 }), 'cash')).toEqual({
      kind: 'cash',
      icon: 'lucideTriangleAlert',
      tone: 'warn',
      title: 'Open buy orders exceed your cash',
      detail: '$250.00 in open buy orders against $100.00 cash',
    });
  });

  it('points a new investor to their first order', () => {
    expect(text(writeBriefing({ ...FACTS, holdings: [], totalValue: 500, openOrders: 1 }))).toEqual(
      [
        ['No investments yet', 'Place an order to start building your portfolio'],
        ['1 order waiting to fill', null],
      ],
    );
  });
});
