import { TestBed } from '@angular/core/testing';
import { OrderNotificationsService } from './order-notifications.service';
import { NotificationsService } from './notifications.service';
import { Order } from '../orders/orders.models';

describe('Order notifications', () => {
  let service: OrderNotificationsService;
  let show: ReturnType<typeof vi.fn>;
  const order = (status: Order['status'], side: Order['side'] = 'SELL') => ({
    orderId: 'order-1', quantity: 3, ticker: 'AAPL', status, side,
  } as Order);
  beforeEach(() => {
    show = vi.fn();
    TestBed.configureTestingModule({ providers: [{ provide: NotificationsService, useValue: { show } }] });
    service = TestBed.inject(OrderNotificationsService);
  });
  it('silently baselines existing orders and announces each status change once', () => {
    service.observe([order('PENDING')]);
    expect(show).not.toHaveBeenCalled();
    service.observe([order('FILLED')]);
    service.observe([order('FILLED')]);
    expect(show).toHaveBeenCalledTimes(1);
    expect(show).toHaveBeenCalledWith('Sold successfully', expect.stringContaining('3 AAPL'), 'success');
  });
  it('does not announce a successful sale until filled', () => {
    service.placed(order('PENDING'));
    expect(show).toHaveBeenCalledTimes(1);
    expect(show).toHaveBeenCalledWith('Order placed', 'Sell 3 AAPL.');
    service.observe([order('PENDING')]);
    service.observe([order('REJECTED')]);
    expect(show).toHaveBeenLastCalledWith('Order rejected', expect.any(String), 'error');
  });
  it('announces immediately filled sales and avoids duplicate polling notifications', () => {
    service.placed(order('FILLED'));
    service.observe([order('FILLED')]);
    expect(show).toHaveBeenCalledTimes(2);
  });
  it('clears the baseline between sessions', () => {
    service.observe([order('PENDING')]);
    service.reset();
    service.observe([order('FILLED')]);
    expect(show).not.toHaveBeenCalled();
  });
});
