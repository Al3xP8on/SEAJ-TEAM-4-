# Builds and packages the NestJS authentication service, login UI and shared OpenAPI contract.
# Uses the repository root as build context and installs only production dependencies in the final image.
FROM node:24-alpine AS ui-builder
WORKDIR /ui
COPY app/frontend/SEAJ-ui/package.json app/frontend/SEAJ-ui/package-lock.json ./
RUN npm ci
COPY app/frontend/SEAJ-ui/angular.json app/frontend/SEAJ-ui/tsconfig*.json ./
COPY app/frontend/SEAJ-ui/src ./src
COPY app/frontend/SEAJ-ui/public ./public
RUN npm run build

FROM node:24-alpine AS builder
WORKDIR /app/backend/backend-nestjs
COPY app/backend/backend-nestjs/package.json app/backend/backend-nestjs/package-lock.json ./
RUN npm ci --legacy-peer-deps
COPY app/backend/backend-nestjs/tsconfig*.json app/backend/backend-nestjs/nest-cli.json ./
COPY app/backend/backend-nestjs/src ./src
COPY app/backend/backend-nestjs/scripts ./scripts
COPY docs/auth-service.yml /docs/auth-service.yml
COPY --from=ui-builder /ui/dist/seaj-ui/browser /app/frontend/SEAJ-ui/dist/seaj-ui/browser
RUN npm run build

FROM node:24-alpine AS production
WORKDIR /app
COPY app/backend/backend-nestjs/package.json app/backend/backend-nestjs/package-lock.json ./
RUN npm ci --omit=dev --legacy-peer-deps
COPY --from=builder /app/backend/backend-nestjs/dist ./dist
EXPOSE 3000
CMD ["node", "dist/main"]
