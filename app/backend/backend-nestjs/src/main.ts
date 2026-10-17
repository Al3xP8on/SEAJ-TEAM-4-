// Starts the authentication HTTP server with validation, error handling and configured CORS.
// Serves the login interface and exposes the canonical OpenAPI contract through Swagger.
// Telemetry starts first so the libraries below are instrumented when they load.
import './observability/telemetry';
import { NestFactory } from '@nestjs/core';
import { SwaggerModule } from '@nestjs/swagger';
import type { OpenAPIObject } from '@nestjs/swagger';
import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { load } from 'js-yaml';
import type { NestExpressApplication } from '@nestjs/platform-express';
import { AppModule } from './app.module';
import { configureHttp } from './http.config';
import { OtelLogger } from './observability/otel-logger';
import { requestLogging } from './observability/request-logging';
async function bootstrap() {
  const app = await NestFactory.create<NestExpressApplication>(AppModule, { logger: new OtelLogger() });
  app.use(requestLogging);
  configureHttp(app);
  const publicPath = join(__dirname, 'public');
  if (existsSync(join(publicPath, 'index.html'))) {
    app.useStaticAssets(publicPath, { index: false });
    app.getHttpAdapter().getInstance().get(
      ['/', '/login', '/login/', '/login/Login.html'],
      (_request: unknown, response: import('express').Response) => response.sendFile(join(publicPath, 'index.html')),
    );
  }
  const origins = (
    process.env.FRONTEND_ORIGINS ||
    'http://localhost:4200,http://127.0.0.1:4200,http://localhost:8085,http://127.0.0.1:8085,http://10.9.69.224:8085,http://localhost:8000,http://127.0.0.1:8000,http://localhost:5500,http://127.0.0.1:5500'
  )
    .split(',')
    .map((origin) => origin.trim())
    .filter(Boolean);
  app.enableCors({
    origin: origins,
    methods: ['GET', 'HEAD', 'PUT', 'PATCH', 'POST', 'DELETE', 'OPTIONS'],
    allowedHeaders: ['Content-Type', 'Authorization'],
  });
  const contractPath = existsSync(join(__dirname, 'openapi.yaml'))
    ? join(__dirname, 'openapi.yaml')
    : join(process.cwd(), '../../../docs/auth-service.yml');
  const document = load(readFileSync(contractPath, 'utf8')) as OpenAPIObject;
  SwaggerModule.setup('api/docs', app, document);
  await app.listen(process.env.PORT ?? 3000, '0.0.0.0');
}
void bootstrap();
