import { UsersService } from './users.service';
import { User } from './user.entity';
import { Repository } from 'typeorm';
import { PasswordService } from '../auth/password.service';
describe('Simulated bank transfers', () => {
  let user: User;
  const update = jest.fn();
  const query = { addSelect: jest.fn().mockReturnThis(), where: jest.fn().mockReturnThis(), setLock: jest.fn().mockReturnThis(), getOne: jest.fn() };
  const repository = { createQueryBuilder: () => query, update };
  const transaction = async (run: (manager: unknown) => Promise<unknown>) => run({ getRepository: () => repository });
  const service = new UsersService({ manager: { transaction }, ...repository } as unknown as Repository<User>, {} as PasswordService);
  const dto = { action: 'deposit' as const, amount: '25.10', request_id: '00000000-0000-4000-8000-000000000001' };
  beforeEach(() => {
    jest.clearAllMocks();
    user = Object.assign(new User(), { id: '7', status: 'ACTIVE', phone: null, bankDetails: { bank_name: 'Test Bank', holder_name: 'Alice', account_number: '1234567890', routing_number: '123456789' }, cashBalance: '100.00', version: 0, cashTransfers: [] });
    query.getOne.mockImplementation(async () => user);
  });
  it('moves exact cents between balances and persists a completed transfer', async () => {
    const result = await service.transfer('7', dto);
    expect(result.cash_balance).toBe('125.10'); 
    expect(result.transfers).toHaveLength(1); expect(result.transfers[0].status).toBe('Completed');
    expect(update).toHaveBeenCalledWith({ id: '7' }, expect.objectContaining({ cashBalance: '125.10', version: 1 }));
    expect(query.setLock).toHaveBeenCalledWith('pessimistic_write');
  });
  it('does not apply the same transfer twice', async () => {
    await service.transfer('7', dto); const result = await service.transfer('7', dto);
    expect(result.cash_balance).toBe('125.10'); expect(result.transfers).toHaveLength(1);
    await expect(service.transfer('7', { ...dto, amount: '26.00' })).rejects.toThrow('Transfer reference already used');
  });
  it('withdraws cash back to the simulated bank', async () => {
    const result = await service.transfer('7', { ...dto, action: 'withdraw' });
    expect(result.cash_balance).toBe('74.90'); 
  });
  it('rejects insufficient balances and missing bank accounts', async () => {
    await expect(service.transfer('7', { ...dto, action: 'withdraw', amount: '101.00' })).rejects.toThrow('Insufficient cash balance');
    user.bankDetails = null;
    await expect(service.transfer('7', dto)).rejects.toThrow('Enter card details or select a saved card'); expect(update).not.toHaveBeenCalled();
  });
  it('accepts a one-off card without persisting the card or its full number', async () => {
    user.bankDetails = null;
    const result = await service.transfer('7', { ...dto, card: { holder_name: 'Alice', card_number: '4111111111111111', expiry_month: 12, expiry_year: 2099 } });
    expect(result.bank).toBeNull(); expect(result.cash_balance).toBe('125.10');
    expect(result.transfers[0].account_last4).toBe('1111'); expect(JSON.stringify(result)).not.toContain('4111111111111111');
    expect(update).toHaveBeenCalledWith({ id: '7' }, expect.objectContaining({ bankDetails: null }));
  });
  it('keeps existing cards and saves an additional card in the same transfer transaction', async () => {
    const oldCard = { ...user.bankDetails!, id: '00000000-0000-4000-8000-000000000002' };
    user.savedCards = [oldCard];
    const request = { ...dto, save_card: true, card: { holder_name: 'Alice', card_number: '4111111111119999', expiry_month: 12, expiry_year: 2099 } };
    const result = await service.transfer('7', request);
    expect(result.cards).toHaveLength(2); expect(result.cards[0].account_last4).toBe('7890'); expect(result.cards[1].account_last4).toBe('9999');
    expect(JSON.stringify(user.savedCards)).not.toContain('4111111111119999');
    await service.transfer('7', request); expect(user.savedCards).toHaveLength(2);
  });
  it('uses the selected saved card and rejects a card not owned by the account', async () => {
    const card = { ...user.bankDetails!, id: '00000000-0000-4000-8000-000000000002' };
    user.savedCards = [card];
    await expect(service.transfer('7', { ...dto, card_id: '00000000-0000-4000-8000-000000000099' })).rejects.toThrow();
    const result = await service.transfer('7', { ...dto, card_id: card.id }); expect(result.cash_balance).toBe('125.10');
  });
  it('does not restrict deposits to the former demo funding balance', async () => {
    const result = await service.transfer('7', { ...dto, amount: '20000.00' }); expect(result.cash_balance).toBe('20100.00');
  });
  it('removes only an owned saved card and clears the default when the last card is removed', async () => {
    const card = { ...user.bankDetails!, id: '00000000-0000-4000-8000-000000000002' };
    user.savedCards = [card]; user.bankDetails = card;
    await expect(service.removeCard('7', 'not-owned')).rejects.toThrow('Card not found');
    const result = await service.removeCard('7', card.id); expect(result.cards).toEqual([]); expect(result.bank).toBeNull();
  });
  it('rejects expired cards', async () => {
    await expect(service.saveBank('7', { holder_name: 'Alice', card_number: '4111111111111111', expiry_month: 1, expiry_year: 2020 })).rejects.toThrow('Card expiry must be in the future'); expect(update).not.toHaveBeenCalled();
  });
  it('masks stored numbers and preserves balances when replacing bank details', async () => {
    const result = await service.saveBank('7', { holder_name: 'Alice', card_number: '4111111111112100', expiry_month: 12, expiry_year: 2099 });
    expect(result.bank?.account_last4).toBe('2100'); expect(JSON.stringify(result)).not.toContain('4111111111112100');
     expect(result.cash_balance).toBe('100.00');
    expect(user.bankDetails?.account_number).toBe('2100');
    expect(result).not.toHaveProperty('simulated_bank_balance');
  });
});
