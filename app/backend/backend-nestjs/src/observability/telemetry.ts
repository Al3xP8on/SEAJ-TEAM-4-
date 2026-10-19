// Starts OpenTelemetry before the app loads, so HTTP, Express, Nest and Postgres calls are traced.
// Configured from OTEL_* environment variables; OTEL_SDK_DISABLED=true turns it off entirely.
import { NodeSDK } from '@opentelemetry/sdk-node';
import { HttpInstrumentation } from '@opentelemetry/instrumentation-http';
import { ExpressInstrumentation } from '@opentelemetry/instrumentation-express';
import { NestInstrumentation } from '@opentelemetry/instrumentation-nestjs-core';
import { PgInstrumentation } from '@opentelemetry/instrumentation-pg';

// Only the libraries this service uses. Headers, request bodies and query values are not recorded
// (the defaults), so passwords, codes, tokens and card numbers stay out of telemetry.
const sdk = new NodeSDK({
  instrumentations: [
    new HttpInstrumentation(),
    new ExpressInstrumentation(),
    new NestInstrumentation(),
    new PgInstrumentation(),
  ],
});
sdk.start();

// Send whatever is still buffered before the container stops
process.once('SIGTERM', () => {
  void sdk.shutdown().finally(() => process.exit(0));
});
