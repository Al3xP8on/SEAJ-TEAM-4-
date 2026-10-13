// Looks up credentials, checks username/email uniqueness and prepares hashed registration data.
// Creates verified accounts with UUID-based identifiers and required balance, version and timestamps.
import { randomUUID } from 'node:crypto';
import { ConflictException, Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import type { EntityManager } from 'typeorm';
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
