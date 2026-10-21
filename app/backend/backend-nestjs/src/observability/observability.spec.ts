// Covers the logger that forwards to the collector and the per-request log line.
// Checks levels, context and stack traces, and that query strings never reach the logs.
import { EventEmitter } from 'node:events';
import { Logger } from '@nestjs/common';
import { logs, SeverityNumber } from '@opentelemetry/api-logs';
import type { NextFunction, Request, Response } from 'express';
import { OtelLogger } from './otel-logger';
import { requestLogging } from './request-logging';

describe('OtelLogger', () => {
  const emit = jest.fn();
  let logger: OtelLogger;

  beforeEach(() => {
    emit.mockClear();
    jest.spyOn(logs, 'getLogger').mockReturnValue({ emit } as never);
    logger = new OtelLogger();
    // Keep test output quiet; the console side is Nest's own ConsoleLogger
    jest.spyOn(process.stdout, 'write').mockReturnValue(true);
    jest.spyOn(process.stderr, 'write').mockReturnValue(true);
  });

  afterEach(() => jest.restoreAllMocks());

  it('sends info lines with their context', () => {
    logger.log('Server started', 'Bootstrap');
    expect(emit).toHaveBeenCalledWith({
      severityNumber: SeverityNumber.INFO,
      severityText: 'INFO',
      body: 'Server started',
      attributes: { 'log.context': 'Bootstrap' },
    });
  });

  it('keeps the stack trace on errors', () => {
    logger.error('Database unavailable', 'Error: at query()', 'ApiErrorFilter');
    expect(emit).toHaveBeenCalledWith(expect.objectContaining({
      severityNumber: SeverityNumber.ERROR,
      body: 'Database unavailable',
      attributes: { 'log.context': 'ApiErrorFilter', 'exception.stacktrace': 'Error: at query()' },
    }));
  });

  it('maps warnings and turns objects into text', () => {
    logger.warn({ reason: 'slow' }, 'HTTP');
    expect(emit).toHaveBeenCalledWith(expect.objectContaining({
      severityNumber: SeverityNumber.WARN,
      body: '{"reason":"slow"}',
    }));
  });
});

describe('requestLogging', () => {
  function run(url: string, statusCode: number) {
    const response = Object.assign(new EventEmitter(), { statusCode }) as unknown as Response;
    const next = jest.fn() as NextFunction;
    requestLogging({ method: 'POST', originalUrl: url } as Request, response, next);
    expect(next).toHaveBeenCalled();
    response.emit('finish');
  }

  afterEach(() => jest.restoreAllMocks());

  it('logs method, path, status and duration without the query string', () => {
    const log = jest.spyOn(Logger.prototype, 'log').mockImplementation();
    run('/auth/login?code=123456', 202);
    expect(log).toHaveBeenCalledWith(expect.stringMatching(/^POST \/auth\/login 202 \d+\.\dms$/));
    expect(log.mock.calls[0][0]).not.toContain('123456');
  });

  it('logs client errors as warnings and server errors as errors', () => {
    const warn = jest.spyOn(Logger.prototype, 'warn').mockImplementation();
    const error = jest.spyOn(Logger.prototype, 'error').mockImplementation();
    run('/auth/login', 401);
    run('/auth/transfers', 503);
    expect(warn).toHaveBeenCalledWith(expect.stringContaining('401'));
    expect(error).toHaveBeenCalledWith(expect.stringContaining('503'));
  });
});
