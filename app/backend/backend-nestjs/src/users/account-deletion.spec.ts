import { UsersService } from './users.service';
import { User } from './user.entity';
import { AuthSession } from '../auth/auth-session.entity';
import { EmailChallenge } from '../auth/email-challenge.entity';
import { Repository } from 'typeorm';
import { PasswordService } from '../auth/password.service';
describe('Account deletion transaction', () => {
  let user: User;
  const update = jest.fn();
  const revoke = jest.fn();
  const execute = jest.fn();
  const deletion = { where: jest.fn().mockReturnThis(), execute };
  const query = { where: jest.fn().mockReturnThis(), setLock: jest.fn().mockReturnThis(), getOne: jest.fn() };
  const users = { createQueryBuilder: () => query, update };
  const manager = { getRepository: (entity: unknown) => entity === User ? users : entity === AuthSession ? { update: revoke } : entity === EmailChallenge ? { createQueryBuilder: () => ({ delete: () => deletion }) } : undefined };
  const service = new UsersService({ ...users, manager: { transaction: (run: (manager: unknown) => Promise<unknown>) => run(manager) } } as unknown as Repository<User>, { hash: jest.fn().mockResolvedValue('unusable-hash') } as unknown as PasswordService);
  beforeEach(() => {
    jest.clearAllMocks(); user = Object.assign(new User(), { id: '7', status: 'ACTIVE', email: 'alice@example.com', cashBalance: '0.00', version: 1 });
    query.getOne.mockImplementation(async () => user);
  });
  it('rejects any remaining cash before changing account data or sessions', async () => {
    user.cashBalance = '0.01';
    await expect(service.deletionAccount('7')).rejects.toThrow('Withdraw all');
    await expect(service.deleteAccount('7', user.email)).rejects.toThrow('Withdraw all');
    expect(update).not.toHaveBeenCalled(); expect(revoke).not.toHaveBeenCalled();
  });
  it('rechecks the locked balance at final confirmation and invalidates sessions', async () => {
    await service.deleteAccount('7', user.email);
    expect(query.setLock).toHaveBeenCalledWith('pessimistic_write');
    expect(update).toHaveBeenCalledWith({ id: '7' }, expect.objectContaining({ status: 'CLOSED', email: null, phone: null, bankDetails: null, savedCards: [], cashTransfers: [], password: 'unusable-hash', username: expect.stringMatching(/^deleted-/) }));
    expect(revoke).toHaveBeenCalledWith({ userId: '7' }, { revoked: true }); expect(execute).toHaveBeenCalled();
  });
  it('rejects deletion if the account email changed after approval', async () => {
    await expect(service.deleteAccount('7', 'old@example.com')).rejects.toThrow('Email changed'); expect(update).not.toHaveBeenCalled();
  });
  it('rejects accounts that have already been deleted', async () => {
    user.status = 'CLOSED'; await expect(service.deleteAccount('7', user.email)).rejects.toThrow('Account is unavailable'); expect(update).not.toHaveBeenCalled();
  });
});
