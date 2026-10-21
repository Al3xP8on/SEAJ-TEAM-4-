/** A holding above this share of the total portfolio is flagged */
export const CONCENTRATION_LIMIT = 0.25;

/** Everything the insights describe, already worked out by the dashboard */
export interface BriefingFacts {
  totalValue: number;
  holdings: { symbol: string; value: number; dayChange: number | null }[];
  cash: number;
  /** Cash held back by open BUY orders; the backend only debits cash when an order fills */
  reserved: number;
  openOrders: number;
  concentrationLimit: number;
}

export type BriefingTone = 'good' | 'bad' | 'warn' | 'neutral';

/** One insight: a short title with the key fact, and an optional detail */
export interface BriefingItem {
  kind: 'start' | 'mover' | 'diversification' | 'cash' | 'orders';
  /** Lucide icon name; decorative, as the words carry the meaning */
  icon: string;
  tone: BriefingTone;
  title: string;
  detail: string | null;
}

const usd = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });
const money = (value: number) => usd.format(Math.abs(value));
const percent = (share: number) => `${Math.round(share * 100)}%`;
const plural = (count: number, word: string) => `${count} ${word}${count === 1 ? '' : 's'}`;

/**
 * What the numbers mean, in plain language: only what the summary cards above don't already
 * show. Direction is always in words ("Up", "Down"), never only in colour or icons.
 */
export function writeBriefing(facts: BriefingFacts): BriefingItem[] {
  const items: BriefingItem[] = [];

  if (facts.holdings.length === 0) {
    items.push({
      kind: 'start',
      icon: 'lucideChartPie',
      tone: 'neutral',
      title: 'No investments yet',
      detail: 'Place an order to start building your portfolio',
    });
  } else {
    const mover = biggestMover(facts);
    if (mover) {
      items.push(mover);
    }
    if (facts.totalValue > 0) {
      items.push(diversification(facts));
    }
  }

  const cash = cashNotice(facts);
  if (cash) {
    items.push(cash);
  }
  if (facts.openOrders > 0) {
    items.push({
      kind: 'orders',
      icon: 'lucideClock',
      tone: 'neutral',
      title: `${plural(facts.openOrders, 'order')} waiting to fill`,
      detail: null,
    });
  }
  return items;
}

function biggestMover(facts: BriefingFacts): BriefingItem | null {
  const movers = facts.holdings.filter((h) => h.dayChange !== null && h.dayChange !== 0);
  if (movers.length === 0) {
    return null;
  }
  const biggest = movers.reduce((a, b) =>
    Math.abs(b.dayChange!) > Math.abs(a.dayChange!) ? b : a,
  );
  const up = biggest.dayChange! > 0;
  return {
    kind: 'mover',
    icon: up ? 'lucideTrendingUp' : 'lucideTrendingDown',
    tone: up ? 'good' : 'bad',
    title: `${biggest.symbol} moved the most`,
    detail: `${up ? 'Up' : 'Down'} ${money(biggest.dayChange!)} today`,
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

/** Only when open buy orders hold cash back; the cash card already shows the balance */
function cashNotice(facts: BriefingFacts): BriefingItem | null {
  if (facts.reserved <= 0) {
    return null;
  }
  if (facts.reserved > facts.cash) {
    return {
      kind: 'cash',
      icon: 'lucideTriangleAlert',
      tone: 'warn',
      title: 'Open buy orders exceed your cash',
      detail: `${money(facts.reserved)} in open buy orders against ${money(facts.cash)} cash`,
    };
  }
  return {
    kind: 'cash',
    icon: 'lucideWallet',
    tone: 'neutral',
    title: `${money(facts.reserved)} set aside`,
    detail: 'Held for open buy orders until they fill',
  };
}
