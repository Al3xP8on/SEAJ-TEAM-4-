// Nest logger that prints to the console as before and also sends each line to the collector (Loki).
// Records carry the active trace, so a log line in Grafana links to its request trace.
import { ConsoleLogger } from '@nestjs/common';
import { logs, SeverityNumber } from '@opentelemetry/api-logs';

type Level = 'log' | 'error' | 'warn' | 'debug' | 'verbose' | 'fatal';

const SEVERITY: Record<Level, [SeverityNumber, string]> = {
  verbose: [SeverityNumber.TRACE, 'TRACE'],
  debug: [SeverityNumber.DEBUG, 'DEBUG'],
  log: [SeverityNumber.INFO, 'INFO'],
  warn: [SeverityNumber.WARN, 'WARN'],
  error: [SeverityNumber.ERROR, 'ERROR'],
  fatal: [SeverityNumber.FATAL, 'FATAL'],
};

export class OtelLogger extends ConsoleLogger {
  // A no-op until the SDK starts, and stays one when telemetry is disabled
  private readonly otel = logs.getLogger('seaj-auth-service');

  override log(message: unknown, ...rest: unknown[]) { super.log(message, ...rest); this.emit('log', message, rest); }
  override error(message: unknown, ...rest: unknown[]) { super.error(message, ...rest); this.emit('error', message, rest); }
  override warn(message: unknown, ...rest: unknown[]) { super.warn(message, ...rest); this.emit('warn', message, rest); }
  override debug(message: unknown, ...rest: unknown[]) { super.debug(message, ...rest); this.emit('debug', message, rest); }
  override verbose(message: unknown, ...rest: unknown[]) { super.verbose(message, ...rest); this.emit('verbose', message, rest); }
  override fatal(message: unknown, ...rest: unknown[]) { super.fatal(message, ...rest); this.emit('fatal', message, rest); }

  private emit(level: Level, message: unknown, rest: unknown[]) {
    // Nest passes the context (e.g. "HTTP") last, and for errors a stack trace before it
    const context = typeof rest.at(-1) === 'string' ? (rest.at(-1) as string) : this.context;
    const stack =
      message instanceof Error ? message.stack
        : (level === 'error' || level === 'fatal') && rest.length > 1 && typeof rest[0] === 'string' ? rest[0]
          : undefined;
    const [severityNumber, severityText] = SEVERITY[level];
    this.otel.emit({
      severityNumber,
      severityText,
      body: message instanceof Error ? message.message : typeof message === 'string' ? message : JSON.stringify(message),
      attributes: {
        ...(context ? { 'log.context': context } : {}),
        ...(stack ? { 'exception.stacktrace': stack } : {}),
      },
    });
  }
}
