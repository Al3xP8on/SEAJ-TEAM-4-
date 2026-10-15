// Looks up credentials, checks username/email uniqueness and prepares hashed registration data.
// Creates verified accounts with UUID-based identifiers and required balance, version and timestamps.
import { randomUUID } from 'node:crypto';
import { ConflictException, Injectable, UnauthorizedException, BadRequestException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import type { EntityManager } from 'typeorm';
import { BankDto, TransferDto } from '../auth/dto/account-settings.dto';
import { AuthSession } from '../auth/auth-session.entity';
import { EmailChallenge } from '../auth/email-challenge.entity';
import { User } from './user.entity';
import { PasswordService } from '../auth/password.service';
import { RegisterDto } from '../auth/dto/register.dto';
import type { RegistrationPayload } from '../auth/email-challenge.entity';
@Injectable()
export class UsersService {
  constructor(
    @InjectRepository(User) private readonly users: Repository<User>,
    private readonly passwords: PasswordService,
  ) {}
  findByUsername(username: string): Promise<User | null> {
    return this.users
      .createQueryBuilder('user')
      .addSelect('user.password')
      .where('user.username = :username', { username })
      .getOne();
  }
  findById(id: string, manager?: EntityManager): Promise<User | null> {
    return (manager?.getRepository(User) ?? this.users).findOneBy({ id });
  }
  findCredentialsById(
    id: string,
    manager: EntityManager,
  ): Promise<User | null> {
    return manager
      .getRepository(User)
      .createQueryBuilder('user')
      .addSelect('user.password')
      .where('user.id = :id', { id })
      .setLock('pessimistic_read')
      .getOne();
  }
  async settings(id: string) {
    const user = await this.users.createQueryBuilder('user').addSelect('user.bankDetails').where('user.id = :id', { id }).getOne();
    if (!user) throw new UnauthorizedException();
    return this.safeSettings(user);
  }
  private safeSettings(user: User) {
    const mask = (card: import('./user.entity').BankDetails) => ({ id: card.id,
      bank_name: card.bank_name, holder_name: card.holder_name, account_last4: card.account_number.slice(-4),
      routing_last4: card.routing_number.slice(-4), expiry_month: card.expiry_month, expiry_year: card.expiry_year, kind: card.kind ?? 'bank' });
    return { phone: user.phone, bank: user.bankDetails ? mask(user.bankDetails) : null,
      cards: (user.savedCards ?? []).map(mask),
      transfers: user.cashTransfers ?? [], cash_balance: user.cashBalance };
  }
  private addCard(user: User, card: import('./user.entity').BankDetails) {
    const cards = user.savedCards ?? [];
    const saved = { ...card, id: randomUUID() };
    user.savedCards = [...cards, saved];
    return saved;
  }
  private async updateSettings(id: string, update: (user: User) => void) {
    return this.users.manager.transaction(async manager => {
      const repository = manager.getRepository(User);
      const user = await repository.createQueryBuilder('user').addSelect('user.bankDetails')
        .where('user.id = :id', { id }).setLock('pessimistic_write').getOne();
      if (!user || user.status !== 'ACTIVE') throw new BadRequestException('Account must be active');
      update(user);
      await repository.update({ id }, { phone: user.phone, bankDetails: user.bankDetails,
        cashBalance: user.cashBalance,
        cashTransfers: user.cashTransfers, savedCards: user.savedCards ?? [], version: user.version + 1, lastUpdated: new Date() });
      return this.safeSettings(user);
    });
  }
  async updatePhone(id: string, phone: string) {
    try { return await this.updateSettings(id, user => { user.phone = phone.trim() || null; }); }
    catch (error) {
      if ((error as { code?: string }).code === '23505') throw new ConflictException('Phone number already in use');
      throw error;
    }
  }
  saveBank(id: string, bank: BankDto) {
    return this.updateSettings(id, user => { user.bankDetails = this.addCard(user, this.cardDetails(bank)); });
  }
  removeCard(id: string, cardId: string) {
    return this.updateSettings(id, user => {
      const cards = user.savedCards ?? [];
      const removed = cards.find(card => card.id === cardId);
      if (!removed) throw new BadRequestException('Card not found');
      user.savedCards = cards.filter(card => card.id !== cardId);
      if (user.bankDetails?.id === cardId || (!user.bankDetails?.id && user.bankDetails?.account_number === removed.account_number)) {
        user.bankDetails = user.savedCards[0] ?? null;
      }
    });
  }
  async deletionAccount(id: string, manager?: EntityManager) {
    const repository = manager?.getRepository(User) ?? this.users;
    const query = repository.createQueryBuilder('user').where('user.id = :id', { id });
    if (manager) query.setLock('pessimistic_write');
    const user = await query.getOne();
    if (!user || user.status !== 'ACTIVE' || !user.email) throw new UnauthorizedException('Account is unavailable');
    if (!/^0(?:\.0+)?$/.test(user.cashBalance)) throw new BadRequestException('Withdraw all of your cash balance before deleting your account');
    return user;
  }
  deleteAccount(id: string, expectedEmail: string) {
    return this.users.manager.transaction(async manager => {
      const user = await this.deletionAccount(id, manager);
      if (user.email !== expectedEmail) throw new UnauthorizedException('Email changed. Request a new deletion code');
      // Keep an anonymized ledger reference for existing trading records.
      await manager.getRepository(User).update({ id }, {
        status: 'CLOSED', username: `deleted-${randomUUID()}`, name: 'Deleted account',
        email: null as unknown as string, phone: null, emailVerified: false,
        password: await this.passwords.hash(randomUUID()), bankDetails: null, savedCards: [], cashTransfers: [],
        roles: 'USER', role: 'USER', version: user.version + 1, lastUpdated: new Date(),
      });
      await manager.getRepository(AuthSession).update({ userId: id }, { revoked: true });
      await manager.getRepository(EmailChallenge).createQueryBuilder().delete()
        .where("email = :email OR payload ->> 'userId' = :id", { email: user.email, id }).execute();
      return { message: 'Account deleted' };
    });
  }
  private cardDetails(card: BankDto) {
    const now = new Date();
    if (!card.holder_name.trim()) throw new BadRequestException('Cardholder name is required');
    if (card.expiry_year < now.getUTCFullYear() || (card.expiry_year === now.getUTCFullYear() && card.expiry_month < now.getUTCMonth() + 1)) {
      throw new BadRequestException('Card expiry must be in the future');
    }
    return { bank_name: 'Simulated payment card', holder_name: card.holder_name.trim(),
      account_number: card.card_number.slice(-4), routing_number: '', expiry_month: card.expiry_month, expiry_year: card.expiry_year, kind: 'card' };
  }
  transfer(id: string, dto: TransferDto) {
    const cents = (value: string) => { const [whole, fraction = ''] = value.split('.'); return BigInt(whole) * 100n + BigInt(fraction.padEnd(2, '0')); };
    const money = (value: bigint) => `${value / 100n}.${String(value % 100n).padStart(2, '0')}`;
    return this.updateSettings(id, user => {
      let card = dto.card ? this.cardDetails(dto.card) : dto.card_id ? (user.savedCards ?? []).find(item => item.id === dto.card_id) : user.bankDetails;
      if (!card) throw new BadRequestException('Enter card details or select a saved card');
      const amount = cents(dto.amount);
      if (amount <= 0n || amount > 100000000n) throw new BadRequestException('Amount must be between 0.01 and 1,000,000');
      const previous = (user.cashTransfers ?? []).find(item => item.id === dto.request_id);
      if (previous) {
        if (previous.action !== dto.action || previous.amount !== dto.amount) throw new ConflictException('Transfer reference already used');
        return;
      }
      const cash = cents(user.cashBalance);
      if (dto.action === 'withdraw' && cash < amount) throw new BadRequestException('Insufficient cash balance');
      user.cashBalance = money(dto.action === 'deposit' ? cash + amount : cash - amount);
      if (dto.card && dto.save_card) { card = this.addCard(user, card); user.bankDetails = card; }
      user.cashTransfers = [{ id: dto.request_id, action: dto.action, amount: dto.amount,
        bank_name: card.bank_name, account_last4: card.account_number.slice(-4),
        created_at: new Date().toISOString(), status: 'Completed' }, ...(user.cashTransfers ?? [])];
    });
  }
  async updateEmail(id: string, email: string, manager: EntityManager) {
    try {
      const result = await manager.getRepository(User).update({ id, status: 'ACTIVE' }, { email: email.toLowerCase(), emailVerified: true, lastUpdated: new Date() });
      if (!result.affected) throw new UnauthorizedException();
    } catch (error) {
      if ((error as { code?: string }).code === '23505') throw new ConflictException('Email already in use');
      throw error;
    }
    return { email: email.toLowerCase() };
  }
  async changePassword(id: string, currentPassword: string, newPassword: string): Promise<void> {
    await this.users.manager.transaction(async manager => {
      const repository = manager.getRepository(User);
      const user = await repository.createQueryBuilder('user')
        .addSelect('user.password').where('user.id = :id', { id })
        .setLock('pessimistic_write').getOne();
      if (!user || user.status !== 'ACTIVE' || !(await this.passwords.verify(currentPassword, user.password))) {
        throw new UnauthorizedException('Current password is incorrect');
      }
      await repository.update({ id }, { password: await this.passwords.hash(newPassword), lastUpdated: new Date() });
    });
  }
  async prepareRegistration(dto: RegisterDto): Promise<RegistrationPayload> {
    const email = dto.email.toLowerCase();
    const phone = dto.phone?.trim() || null;
    const duplicate = await this.users
      .createQueryBuilder('user')
      .where('user.username = :username OR LOWER(user.email) = :email', {
        username: dto.username,
        email,
      })
      .getExists();
    if (duplicate)
      throw new ConflictException('Email or username already exists');
    if (phone && (await this.users.existsBy({ phone })))
      throw new ConflictException('Phone number already in use');
    return {
      username: dto.username,
      email,
      name: dto.name ?? dto.username,
      passwordHash: await this.passwords.hash(dto.password),
      phone,
    };
  }
  async registerVerified(
    payload: RegistrationPayload,
    manager: EntityManager,
  ): Promise<User> {
    const repository = manager.getRepository(User);
    const createdAt = new Date();
    try {
      const saved = await repository.save(
        repository.create({
          accountId: `ACC-${randomUUID().slice(0, 8).toUpperCase()}`,
          username: payload.username,
          email: payload.email,
          phone: payload.phone ?? null,
          name: payload.name,
          password: payload.passwordHash,
          // Shared accounts tables created by Hibernate may have no SQL defaults.
          cashBalance: '0',
          version: 0,
          createdOn: createdAt,
          lastUpdated: createdAt,
          roles: 'USER',
          role: 'USER',
          status: 'ACTIVE',
          emailVerified: true,
        }),
      );
      return await repository.findOneByOrFail({ id: saved.id });
    } catch (error) {
      if ((error as { code?: string }).code === '23505')
        throw new ConflictException('Email or username already exists');
      throw error;
    }
  }
}
