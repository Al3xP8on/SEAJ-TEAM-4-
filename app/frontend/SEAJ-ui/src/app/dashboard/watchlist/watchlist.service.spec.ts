import { TestBed } from '@angular/core/testing';
import { WATCHLIST_LIMIT, WatchlistService } from './watchlist.service';

describe('WatchlistService', () => {
  let service: WatchlistService;

  beforeEach(() => {
    localStorage.clear();
    service = TestBed.inject(WatchlistService);
  });

  afterEach(() => vi.restoreAllMocks());

  it('saves symbols per account and reloads them', () => {
    service.use('ACC-1');
    service.add(' aapl ');
    service.add('MSFT');
    service.add('AAPL'); // already there

    expect(service.symbols()).toEqual(['AAPL', 'MSFT']);
    expect(JSON.parse(localStorage.getItem('seaj.watchlist.ACC-1')!)).toEqual(['AAPL', 'MSFT']);

    service.use('ACC-2');
    expect(service.symbols()).toEqual([]);
    service.use('ACC-1');
    expect(service.symbols()).toEqual(['AAPL', 'MSFT']);
  });

  it('removes symbols', () => {
    service.use('ACC-1');
    service.add('AAPL');
    service.add('MSFT');
    service.remove('AAPL');

    expect(service.symbols()).toEqual(['MSFT']);
  });

  it('stops at the limit', () => {
    service.use('ACC-1');
    for (let i = 0; i < WATCHLIST_LIMIT + 3; i++) {
      service.add(`S${i}`);
    }

    expect(service.symbols().length).toBe(WATCHLIST_LIMIT);
  });

  it('ignores damaged saved data', () => {
    localStorage.setItem('seaj.watchlist.ACC-1', '{not json');
    service.use('ACC-1');

    expect(service.symbols()).toEqual([]);
  });

  it('keeps working for the visit when storage is blocked', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    service.use('ACC-1');
    service.add('AAPL');

    expect(service.symbols()).toEqual(['AAPL']);
  });
});
