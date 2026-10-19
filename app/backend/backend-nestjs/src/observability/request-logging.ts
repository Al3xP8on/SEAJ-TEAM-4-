// Logs one line per request once the response is sent: method, path, status and duration.
// Never logs headers, bodies or query strings, which can hold tokens, codes or card details.
import { Logger } from '@nestjs/common';
import type { NextFunction, Request, Response } from 'express';

const logger = new Logger('HTTP');

export function requestLogging(request: Request, response: Response, next: NextFunction): void {
  const started = process.hrtime.bigint();
  response.once('finish', () => {
    const ms = Number(process.hrtime.bigint() - started) / 1e6;
    const path = request.originalUrl.split('?')[0];
    const line = `${request.method} ${path} ${response.statusCode} ${ms.toFixed(1)}ms`;
    if (response.statusCode >= 500) logger.error(line);
    else if (response.statusCode >= 400) logger.warn(line);
    else logger.log(line);
  });
  next();
}
