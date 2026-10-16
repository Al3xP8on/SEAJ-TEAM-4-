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

const usd = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });
const money = (value: number) => usd.format(Math.abs(value));
const percent = (share: number, digits = 0) => `${(share * 100).toFixed(digits)}%`;
const plural = (count: number, word: string) => `${count} ${word}${count === 1 ? '' : 's'}`;

/**
 * The dashboard's numbers as a few plain sentences, so the state of the account can be
 * understood without reading tables and charts. Direction is always in words ("up", "down"),
 * never only in colour or symbols.
 */
export function writeBriefing(facts: BriefingFacts): string[] {
  const sentences: string[] = [];

  if (facts.holdings.length === 0) {
    sentences.push(
      `You don't hold any investments yet. You have ${money(facts.buyingPower)} available to trade.`,
    );
    if (facts.openOrders > 0) {
      sentences.push(waiting(facts.openOrders));
    }
    return sentences;
  }

  const worth = `Your portfolio is worth ${money(facts.totalValue)}`;
  if (facts.atCost) {
    sentences.push(
      `${worth}, with holdings valued at what you paid, as live prices aren't available.`,
    );
  } else if (facts.dayChange && facts.dayChange.amount !== 0) {
    const direction = facts.dayChange.amount > 0 ? 'up' : 'down';
    sentences.push(
      `${worth}, ${direction} ${money(facts.dayChange.amount)} (${percent(Math.abs(facts.dayChange.percent), 2)}) today.`,
    );
  } else {
    sentences.push(`${worth}.`);
  }

  const movers = facts.holdings.filter((h) => h.dayChange !== null && h.dayChange !== 0);
  if (movers.length > 0) {
    const biggest = movers.reduce((a, b) =>
      Math.abs(b.dayChange!) > Math.abs(a.dayChange!) ? b : a,
    );
    const direction = biggest.dayChange! > 0 ? 'up' : 'down';
    sentences.push(
      `${biggest.symbol} moved the most today, ${direction} ${money(biggest.dayChange!)}.`,
    );
  }

  if (facts.totalValue > 0) {
    const over = facts.holdings
      .map((h) => ({ symbol: h.symbol, share: h.value / facts.totalValue }))
      .filter((h) => h.share > facts.concentrationLimit)
      .sort((a, b) => b.share - a.share);
    const guideline = `the ${percent(facts.concentrationLimit)} guideline`;
    if (over.length === 1) {
      sentences.push(
        `${over[0].symbol} makes up ${percent(over[0].share)} of your portfolio, above ${guideline}.`,
      );
    } else if (over.length > 1) {
      const names = over.map((h) => h.symbol);
      sentences.push(
        `${names.slice(0, -1).join(', ')} and ${names.at(-1)} are each above ${guideline}.`,
      );
    } else {
      sentences.push(`No single holding is above ${guideline}.`);
    }
  }

  const reserved =
    facts.reserved > 0 ? `, after ${money(facts.reserved)} set aside for open buy orders` : '';
  sentences.push(`You have ${money(facts.buyingPower)} available to trade${reserved}.`);

  if (facts.openOrders > 0) {
    sentences.push(waiting(facts.openOrders));
  }
  return sentences;
}

function waiting(count: number): string {
  return `${plural(count, 'order')} ${count === 1 ? 'is' : 'are'} waiting to fill.`;
}
