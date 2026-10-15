import { UsersService } from './users.service';
import { User } from './user.entity';
import { Repository } from 'typeorm';
import { PasswordService } from '../auth/password.service';
describe('Password changes', () => {
  const update = jest.fn();
  const getOne = jest.fn();
  const query = { addSelect: jest.fn().mockReturnThis(), where: jest.fn().mockReturnThis(), setLock: jest.fn().mockReturnThis(), getOne };
  const repository = { createQueryBuilder: () => query, update };
  const transaction = jest.fn(async (run: (manager: unknown) => Promise<void>) => run({ getRepository: () => repository }));
  const passwords = { verify: jest.fn(), hash: jest.fn() };
  const service = new UsersService({ manager: { transaction } } as unknown as Repository<User>, passwords as unknown as PasswordService);
  beforeEach(() => { jest.clearAllMocks(); getOne.mockResolvedValue({ password: 'old-hash', status: 'ACTIVE' }); passwords.verify.mockResolvedValue(true); passwords.hash.mockResolvedValue('new-hash'); });
  it('checks the current password and stores only the new hash under a write lock', async () => {
    await service.changePassword('7', 'current', 'new-password');
    expect(passwords.verify).toHaveBeenCalledWith('current', 'old-hash');
    expect(query.where).toHaveBeenCalledWith('user.id = :id', { id: '7' });
    expect(query.setLock).toHaveBeenCalledWith('pessimistic_write');
    expect(update).toHaveBeenCalledWith({ id: '7' }, { password: 'new-hash', lastUpdated: expect.any(Date) });
  });
  it('rejects an incorrect current password without writing', async () => {
    passwords.verify.mockResolvedValue(false);
    await expect(service.changePassword('7', 'wrong', 'new-password')).rejects.toThrow('Current password is incorrect');
    expect(update).not.toHaveBeenCalled(); expect(passwords.hash).not.toHaveBeenCalled();
  });
  it('rejects inactive accounts without writing', async () => {
    getOne.mockResolvedValue({ status: 'SUSPENDED' });
    await expect(service.changePassword('7', 'current', 'new-password')).rejects.toThrow();
    expect(update).not.toHaveBeenCalled();
  });
});
