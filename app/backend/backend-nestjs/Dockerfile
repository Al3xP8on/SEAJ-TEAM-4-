# Builds and packages the NestJS authentication service, login UI and shared OpenAPI contract.
# Uses the repository root as build context and installs only production dependencies in the final image.
FROM node:20-alpine AS builder
WORKDIR /app/backend/backend-nestjs
COPY app/backend/backend-nestjs/package.json app/backend/backend-nestjs/package-lock.json ./
RUN npm ci --legacy-peer-deps
COPY app/backend/backend-nestjs/tsconfig*.json app/backend/backend-nestjs/nest-cli.json ./
COPY app/backend/backend-nestjs/src ./src
COPY app/backend/backend-nestjs/scripts ./scripts
COPY docs/auth-service.yml /docs/auth-service.yml
COPY app/frontend/Login /app/frontend/Login
RUN npm run build

FROM node:20-alpine AS production
WORKDIR /app
COPY app/backend/backend-nestjs/package.json app/backend/backend-nestjs/package-lock.json ./
RUN npm ci --omit=dev --legacy-peer-deps
COPY --from=builder /app/backend/backend-nestjs/dist ./dist
EXPOSE 3000
CMD ["node", "dist/main"]
