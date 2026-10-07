import { Body, Controller, Post } from '@nestjs/common';
import {
  ApiOperation,
  ApiResponse,
  ApiBody,
  ApiTags,
} from '@nestjs/swagger';
import { AuthService } from './auth.service';
import { LoginDto } from './dto/login.dto';
import { AuthResponseDto } from './dto/auth.dto';

@ApiTags('auth')
@Controller('auth')
export class AuthController {
  constructor(private readonly authService: AuthService) {}

  @Post('login')
  @ApiOperation({ summary: 'User login with username and password' })
  @ApiBody({ type: LoginDto })
  @ApiResponse({
    status: 200,
    description: 'Successful login',
    type: AuthResponseDto,
  })
  @ApiResponse({
    status: 400,
    description: 'Bad Request - Missing or invalid fields',
    schema: {
      example: {
        message: 'validation error',
        error: 'Bad Request',
        statusCode: 400,
      },
    },
  })
  @ApiResponse({
    status: 401,
    description: 'Unauthorized - Invalid credentials',
    schema: {
      example: {
        message: 'Invalid credentials',
        error: 'Unauthorized',
        statusCode: 401,
      },
    },
  })
  async login(
    @Body() loginDto: LoginDto,
  ): Promise<AuthResponseDto> {
    return this.authService.login(loginDto);
  }

  @Post('token')
  @ApiOperation({ summary: 'Issue JWT token (stub endpoint)' })
  @ApiBody({
    schema: {
      type: 'object',
      properties: {
        sub: { type: 'string', example: 'user-123' },
        roles: { type: 'array', items: { type: 'string' }, example: ['USER'] },
      },
    },
  })
  @ApiResponse({
    status: 200,
    description: 'JWT token issued',
    schema: {
      example: {
        token: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...',
      },
    },
  })
  issueToken(@Body() body: { sub?: string; roles?: string[] }) {
    const sub = body.sub ?? 'stub-user';
    const roles = body.roles ?? ['USER'];
    return { token: this.authService.issueToken(sub, roles) };
  }
}
