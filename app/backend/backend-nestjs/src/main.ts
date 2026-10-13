// Starts the authentication HTTP server with validation, error handling and configured CORS.
// Serves the login interface and exposes the canonical OpenAPI contract through Swagger.
import { NestFactory } from '@nestjs/core';
import { SwaggerModule } from '@nestjs/swagger';
import type { OpenAPIObject } from '@nestjs/swagger';
import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { load } from 'js-yaml';
import type { NestExpressApplication } from '@nestjs/platform-express';
import { AppModule } from './app.module';
import { configureHttp } from './http.config';
async function bootstrap() {
  const app = await NestFactory.create<NestExpressApplication>(AppModule);
  configureHttp(app);
  const publicPath = existsSync(join(__dirname, 'public'))
    ? join(__dirname, 'public')
    : join(process.cwd(), '..', '..', 'frontend', 'Login');
  app.useStaticAssets(publicPath, { prefix: '/login/' });
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
