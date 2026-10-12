import { PasswordService } from './password.service';
describe('PasswordService', () => {
  const service = new PasswordService();
  it('hashes with a generated salt and never stores plaintext', async () => {
    const first = await service.hash('SecurePass123!');
    const second = await service.hash('SecurePass123!');
    expect(first).not.toBe('SecurePass123!');
    expect(first).not.toBe(second);
    expect(first).toMatch(/^\$2[aby]\$12\$/);
    expect(await service.verify('SecurePass123!', first)).toBe(true);
    expect(await service.verify('WrongPassword!', first)).toBe(false);
  });
});
