// Validates the shared authentication OpenAPI contract and compares it with controller metadata.
// Checks endpoint paths, methods, success statuses and protected-route declarations.
import 'reflect-metadata';
import SwaggerParser from '@apidevtools/swagger-parser';
import { readFileSync } from 'node:fs';
import { load } from 'js-yaml';
import {
  HTTP_CODE_METADATA,
  METHOD_METADATA,
  PATH_METADATA,
  GUARDS_METADATA,
} from '@nestjs/common/constants';
import { RequestMethod } from '@nestjs/common';
import type { OpenAPIObject } from '@nestjs/swagger';
import { AuthController } from './auth.controller';

type OperationObject = NonNullable<OpenAPIObject['paths'][string]['get']>;

describe('Canonical OpenAPI contract', () => {
  const contract = load(readFileSync('../../../docs/auth-service.yml', 'utf8')) as OpenAPIObject;
  it('passes OpenAPI validation', async () => {
    await expect(SwaggerParser.validate('../../../docs/auth-service.yml')).resolves.toBeDefined();
  });
  it('matches every controller path, HTTP method, success status and protection', () => {
    const actual: string[] = [];
    for (const name of Object.getOwnPropertyNames(AuthController.prototype)) {
      const method = Object.getOwnPropertyDescriptor(
        AuthController.prototype,
        name,
      )?.value as unknown;
      if (typeof method !== 'function') continue;
      const requestMethod = Reflect.getMetadata(METHOD_METADATA, method) as
        RequestMethod | undefined;
      if (requestMethod === undefined) continue;
      const path = `/${Reflect.getMetadata(PATH_METADATA, AuthController)}/${Reflect.getMetadata(PATH_METADATA, method)}`;
      const verb = RequestMethod[requestMethod].toLowerCase();
      const operation = (contract.paths[path] as Record<string, unknown>)?.[
        verb
      ] as OperationObject;
      expect(operation).toBeDefined();
      const successStatus =
        Reflect.getMetadata(HTTP_CODE_METADATA, method) ??
        (requestMethod === RequestMethod.POST ? 201 : 200);
      expect(operation.responses[String(successStatus)]).toBeDefined();
      expect(Boolean(Reflect.getMetadata(GUARDS_METADATA, method))).toBe(
        Boolean(operation.security?.length),
      );
      actual.push(`${verb} ${path}`);
    }
    const expected = Object.entries(contract.paths).flatMap(([path, item]) =>
      Object.keys(item!)
        .filter((verb) => ['post', 'get'].includes(verb))
        .map((verb) => `${verb} ${path}`),
    );
    expect(actual.sort()).toEqual(expected.sort());
  });
});
