/** Everything the briefing describes, already worked out by the dashboard */
export interface BriefingFacts {
  totalValue: number;
  /** null when no previous close is known */
  dayChange: { amount: number; percent: number } | null;
  /** true when no holding has a live price, so values are at cost */
  atCost: boolean;
  holdings: { symbol: string; value: number; dayChange: number | null }[];
  buyingPower: number;
  reserved: number;
  openOrders: number;
  concentrationLimit: number;
}

export type BriefingTone = 'good' | 'bad' | 'warn' | 'neutral';

/** One fact about the account: a short title with the key figure, and an optional detail */
export interface BriefingItem {
  kind: 'value' | 'mover' | 'diversification' | 'cash' | 'orders';
  /** Lucide icon name; decorative, as the words carry the meaning */
  icon: string;
  tone: BriefingTone;
  title: string;
  detail: string | null;
}

const usd = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });
const money = (value: number) => usd.format(Math.abs(value));
const percent = (share: number, digits = 0) => `${(share * 100).toFixed(digits)}%`;
const plural = (count: number, word: string) => `${count} ${word}${count === 1 ? '' : 's'}`;

/**
 * The dashboard's numbers as a few plain-language facts, so the state of the account can be
 * understood without reading tables and charts. Direction is always in words ("Up", "Down"),
 * never only in colour or icons.
 */
export function writeBriefing(facts: BriefingFacts): BriefingItem[] {
  const items: BriefingItem[] = [];

  if (facts.holdings.length === 0) {
    items.push({
      kind: 'value',
      icon: 'lucideWallet',
      tone: 'neutral',
      title: 'No investments yet',
      detail: `You have ${money(facts.buyingPower)} available to trade.`,
    });
    if (facts.openOrders > 0) {
      items.push(waiting(facts.openOrders));
    }
    return items;
  }

  items.push(value(facts));

  const movers = facts.holdings.filter((h) => h.dayChange !== null && h.dayChange !== 0);
  if (movers.length > 0) {
    const biggest = movers.reduce((a, b) =>
      Math.abs(b.dayChange!) > Math.abs(a.dayChange!) ? b : a,
    );
    const up = biggest.dayChange! > 0;
    items.push({
      kind: 'mover',
      icon: 'lucideActivity',
      tone: up ? 'good' : 'bad',
      title: `${biggest.symbol} moved the most`,
      detail: `${up ? 'Up' : 'Down'} ${money(biggest.dayChange!)} today`,
    });
  }

  if (facts.totalValue > 0) {
    items.push(diversification(facts));
  }

  items.push({
    kind: 'cash',
    icon: 'lucideWallet',
    tone: 'neutral',
    title: `${money(facts.buyingPower)} to trade`,
    detail:
      facts.reserved > 0 ? `After ${money(facts.reserved)} set aside for open buy orders` : null,
  });

  if (facts.openOrders > 0) {
    items.push(waiting(facts.openOrders));
  }
  return items;
}

function value(facts: BriefingFacts): BriefingItem {
  const title = `Worth ${money(facts.totalValue)}`;
  if (facts.atCost) {
    return {
      kind: 'value',
      icon: 'lucideChartLine',
      tone: 'neutral',
      title,
      detail: "Valued at what you paid, as live prices aren't available",
    };
  }
  const change = facts.dayChange;
  if (!change || change.amount === 0) {
    return { kind: 'value', icon: 'lucideChartLine', tone: 'neutral', title, detail: null };
  }
  const up = change.amount > 0;
  return {
    kind: 'value',
    icon: up ? 'lucideTrendingUp' : 'lucideTrendingDown',
    tone: up ? 'good' : 'bad',
    title,
    detail: `${up ? 'Up' : 'Down'} ${money(change.amount)} (${percent(Math.abs(change.percent), 2)}) today`,
  };
}

function diversification(facts: BriefingFacts): BriefingItem {
  const over = facts.holdings
    .map((h) => ({ symbol: h.symbol, share: h.value / facts.totalValue }))
    .filter((h) => h.share > facts.concentrationLimit)
    .sort((a, b) => b.share - a.share);
  const limit = percent(facts.concentrationLimit);
  if (over.length === 0) {
    return {
      kind: 'diversification',
      icon: 'lucideChartPie',
      tone: 'good',
      title: 'Well spread',
      detail: `No single holding is above ${limit} of your portfolio`,
    };
  }
  const names = over.map((h) => h.symbol);
  return {
    kind: 'diversification',
    icon: 'lucideTriangleAlert',
    tone: 'warn',
    title:
      over.length === 1
        ? `${names[0]} is ${percent(over[0].share)} of your portfolio`
        : `${names.slice(0, -1).join(', ')} and ${names.at(-1)} are each above ${limit}`,
    detail: `Above the ${limit} guideline`,
  };
}

function waiting(count: number): BriefingItem {
  return {
    kind: 'orders',
    icon: 'lucideClock',
    tone: 'neutral',
    title: `${plural(count, 'order')} waiting to fill`,
    detail: null,
  };
}
