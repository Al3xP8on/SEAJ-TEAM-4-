import { Test, TestingModule } from '@nestjs/testing';
import { AuthController } from './auth.controller';
import { AuthService } from './auth.service';

const mockAuthService = { issueToken: jest.fn().mockReturnValue('signed.jwt.token') };

describe('AuthController', () => {
  let controller: AuthController;

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      controllers: [AuthController],
      providers: [{ provide: AuthService, useValue: mockAuthService }],
    }).compile();

    controller = module.get<AuthController>(AuthController);
  });

  it('uses default sub and roles when body is empty', () => {
    expect(controller.issueToken({})).toEqual({ token: 'signed.jwt.token' });
    expect(mockAuthService.issueToken).toHaveBeenCalledWith('stub-user', ['USER']);
  });

  it('passes provided sub and roles to AuthService', () => {
    expect(controller.issueToken({ sub: 'alice', roles: ['ADMIN'] })).toEqual({
      token: 'signed.jwt.token',
    });
    expect(mockAuthService.issueToken).toHaveBeenCalledWith('alice', ['ADMIN']);
  });
});
