# SEAJ Trading Platform - Backend

Unified Spring Boot application containing Account, Order, and Positions services in a single container.

## Quick Start

### Prerequisites
- Java 21 JDK
- Maven 3.9.5+
- Docker & Docker Compose
- PostgreSQL 15+ (or use Docker)

### Setup & Run

```bash
# 1. Navigate to backend directory
cd app/backend

# 2. Build the application
mvn clean package

# 3. Start with Docker Compose
docker-compose up -d

# 4. Test the application
curl http://localhost:8080/api/gateway/health
```

## Project Structure

Simple flat structure with single pom.xml:

```
backend/
├── pom.xml                    # All dependencies in one place
├── Dockerfile                 # Multi-stage Docker build
│
├── src/main/java/com/neueda/leap/
│   ├── gateway/               # API Gateway (routing)
│   │   └── controller/
│   ├── account/               # Account Service
│   │   ├── controller/
│   │   ├── service/
│   │   ├── repository/
│   │   ├── entity/
│   │   └── dto/
│   ├── order/                 # Order Service
│   │   ├── controller/
│   │   ├── service/
│   │   ├── repository/
│   │   ├── entity/
│   │   └── dto/
│   ├── position/              # Positions Service
│   │   ├── controller/
│   │   ├── service/
│   │   ├── repository/
│   │   ├── entity/
│   │   └── dto/
│   ├── shared/                # Shared utilities & exceptions
│   │   └── exception/
│   └── SeajApplication.java   # Entry point
│
└── src/main/resources/
    └── application.properties  # Spring Boot config
```

## Services Overview

All services run in a single Spring Boot application:

| Service | Base Path | Responsibility |
|---------|-----------|-----------------|
| Gateway | `/api/gateway` | Health checks, routing info |
| Account | `/api/accounts` | Account management & balances |
| Order | `/api/orders` | Order management & execution |
| Position | `/api/positions` | Position tracking & P&L |

## API Endpoints

### Gateway
```
GET  /api/gateway/health       - Health check
GET  /api/gateway/info         - Platform info
```

### Account Service
```
GET    /api/accounts           - List all accounts
POST   /api/accounts           - Create account
GET    /api/accounts/{id}      - Get account details
PUT    /api/accounts/{id}      - Update account
DELETE /api/accounts/{id}      - Delete account
```

### Order Service
```
GET    /api/orders             - List all orders
POST   /api/orders             - Create order
GET    /api/orders/{id}        - Get order details
PUT    /api/orders/{id}        - Update order
DELETE /api/orders/{id}        - Cancel order
```

### Positions Service
```
GET    /api/positions          - List all positions
GET    /api/positions/{id}     - Get position details
GET    /api/positions/accounts/{accountId} - Get account positions
```

## Building

```bash
# Full build with tests
mvn clean package

# Faster build (skip tests)
mvn clean package -DskipTests

# Run tests only
mvn test

# Build specific service
mvn test -pl 'com.neueda.leap:seaj-*'
```

## Running

### With Docker Compose (Recommended)
```bash
# Start all services
docker-compose up -d

# View application logs
docker-compose logs -f app

# View database logs
docker-compose logs -f db

# Stop services
docker-compose down

# Clean everything (remove volumes)
docker-compose down -v
```

### Local Development
```bash
# Start database only
docker-compose up -d db

# Wait for database to be ready
sleep 10

# Run application in IDE or via Maven
mvn spring-boot:run
```

## Configuration

Main configuration: `src/main/resources/application.properties`

### Key Properties
```properties
# Server
server.port=8080

# Database
spring.datasource.url=jdbc:postgresql://db:5432/SEAJ_db_DEMO
spring.datasource.username=postgres
spring.datasource.password=postgres

# JPA/Hibernate
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false

# Logging
logging.level.root=INFO
logging.level.com.neueda.leap=DEBUG
logging.file.name=logs/app.log
```

### Override with Environment Variables
```bash
export DB_HOST=my-db-server
export APP_PORT=9000
export POSTGRES_PASSWORD=my-secret
docker-compose up -d
```

## Development

### Adding a New Endpoint

**Example: Add KYC endpoint to Account Service**

1. Create entity:
```java
// src/main/java/com/neueda/leap/account/entity/AccountKYC.java
@Entity
@Table(name = "account_kyc")
public class AccountKYC {
    @Id
    private Long id;
    // fields...
}
```

2. Create repository:
```java
// src/main/java/com/neueda/leap/account/repository/AccountKYCRepository.java
@Repository
public interface AccountKYCRepository extends JpaRepository<AccountKYC, Long> {
}
```

3. Create service:
```java
// src/main/java/com/neueda/leap/account/service/AccountKYCService.java
@Service
public class AccountKYCService {
    @Autowired
    private AccountKYCRepository repository;
    
    public AccountKYC create(AccountKYC kyc) {
        return repository.save(kyc);
    }
}
```

4. Create controller:
```java
// src/main/java/com/neueda/leap/account/controller/AccountKYCController.java
@RestController
@RequestMapping("/api/accounts/kyc")
public class AccountKYCController {
    @Autowired
    private AccountKYCService service;
    
    @PostMapping
    public ResponseEntity<?> createKYC(@RequestBody KYCRequest request) {
        return ResponseEntity.ok(service.create(request));
    }
}
```

5. Build and test:
```bash
mvn clean test
docker-compose up -d
curl -X POST http://localhost:8080/api/accounts/kyc -H "Content-Type: application/json" -d '{...}'
```

## Troubleshooting

### Application won't start
```bash
# Check application logs
docker-compose logs app

# Check database logs
docker-compose logs db

# Rebuild everything
docker-compose down -v
mvn clean package -DskipTests
docker-compose build --no-cache
docker-compose up -d
```

### Port already in use
```bash
# Find process on port 8080
lsof -i :8080

# Kill it
kill -9 <PID>

# Or use different port
export APP_PORT=9000
docker-compose up -d
```

### Database connection error
```bash
# Check if database is running
docker-compose ps

# Check database logs
docker-compose logs db

# Restart database
docker-compose restart db

# Wait for it to be ready
docker-compose exec db pg_isready -U postgres
```

### Compilation errors
```bash
# Clean and rebuild
mvn clean

# Check dependencies
mvn dependency:tree

# Verbose output
mvn -X clean package
```

## Performance Tips

1. Enable query result caching
2. Use database indices on frequently queried columns
3. Implement pagination for large result sets
4. Use lazy loading for related entities
5. Connection pooling is configured by default (HikariCP)

## Security

- ✅ Validate all input
- ✅ Parameterized queries prevent SQL injection (JPA)
- ✅ Use environment variables for sensitive data
- ✅ Add authentication on sensitive endpoints
- ✅ Use HTTPS in production
- ✅ Log security events

## Docker Compose Services

### app
- Spring Boot application
- Port: 8080
- Depends on: db
- Auto-restart on failure

### db
- PostgreSQL 15
- Port: 5432
- Persistent volume: pgdata_volume
- Auto-restart on failure
- Health check enabled

## CI/CD Pipeline

The Jenkins pipeline (`Jenkinsfile` in project root) handles:
1. Building application with `mvn clean package`
2. Building Docker image
3. Running smoke tests on endpoints
4. Optional: Database schema updates

## Documentation

- [API Specifications](../../docs/)
- [Database Schema](../../infra/db/)
- [Environment Configuration](.env.example)
- [Jenkins Pipeline](../../Jenkinsfile)

---

**Version:** 0.1.0  
**Last Updated:** 2026-09-23  
**Architecture:** Single Spring Boot JAR with organized packages
