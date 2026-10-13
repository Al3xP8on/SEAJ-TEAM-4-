// Applies request DTO validation and a consistent error response format across the API.
// Rejects unexpected fields and hides internal exception details from clients.
import {
  Catch,
  HttpException,
  HttpStatus,
  ValidationPipe,
} from '@nestjs/common';
import type {
  ArgumentsHost,
  ExceptionFilter,
  INestApplication,
} from '@nestjs/common';
import type { Response } from 'express';
@Catch()
export class ApiErrorFilter implements ExceptionFilter {
  catch(exception: unknown, host: ArgumentsHost): void {
    const statusCode =
      exception instanceof HttpException ? exception.getStatus() : 500;
    const details =
      exception instanceof HttpException ? exception.getResponse() : undefined;
    const message =
      typeof details === 'string'
        ? details
        : typeof details === 'object' &&
            details !== null &&
            'message' in details
          ? (details as { message: unknown }).message
          : 'Internal server error';
    host
      .switchToHttp()
      .getResponse<Response>()
      .status(statusCode)
      .json({
        statusCode,
        message: Array.isArray(message)
          ? message.join('; ')
          : typeof message === 'string'
            ? message
            : 'Request failed',
        error: HttpStatus[statusCode]?.replace(/_/g, ' ') ?? 'ERROR',
      });
  }
}
export function configureHttp(app: INestApplication): void {
  app.useGlobalPipes(
    new ValidationPipe({
      whitelist: true,
      forbidNonWhitelisted: true,
      transform: true,
    }),
  );
  app.useGlobalFilters(new ApiErrorFilter());
}
